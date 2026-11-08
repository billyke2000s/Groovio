package com.groovio.app.online

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Gets audio the way the owner's Discord bot does (Lavalink youtube-source 1.18.2 with its
 * patched Samsung-TV client), but entirely on the phone:
 *  - asks YouTube's player API as different YouTube apps until one is allowed;
 *  - the TV client's scrambled URLs are decoded with NewPipe Extractor's JavaScript engine
 *    (on-device), instead of the bot's separate yt-cipher server.
 * Blocking network calls: use a background thread.
 */
class InnertubeAudio(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** One YouTube app identity, as configured in youtube-source. */
    data class ClientSpec(
        val name: String,
        val version: String,
        val userAgent: String,
        val extraClientFields: Map<String, Any> = emptyMap(),
        val params: String? = null,
        /** Needs the player script: signature timestamp + URL descrambling. */
        val scrambled: Boolean,
        /** Non-OAuth clients pretend to be an embedded player (youtube-source behaviour). */
        val embedded: Boolean,
    )

    class BlockedException(message: String) : IOException(message)

    private var visitorData: String? = null
    private var visitorFetchedAt = 0L

    /** The best audio from the first client YouTube serves, or throws with the last reason. */
    fun resolve(videoId: String): ResolvedAudio {
        var lastError: Exception? = null
        for (client in CLIENTS) {
            try {
                return resolveWith(client, videoId)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("No YouTube client could load $videoId")
    }

    fun resolveWith(client: ClientSpec, videoId: String): ResolvedAudio {
        val body = JsonObject()
        val context = JsonObject()
        val clientObj = JsonObject()
        clientObj["clientName"] = client.name
        clientObj["clientVersion"] = client.version
        client.extraClientFields.forEach { (k, v) -> clientObj[k] = v }
        if (client.embedded) {
            clientObj["clientScreen"] = "EMBED"
            context["thirdParty"] = JsonObject().apply { put("embedUrl", "https://google.com") }
        }
        context["client"] = clientObj
        body["context"] = context
        body["videoId"] = videoId
        body["racyCheckOk"] = true
        body["contentCheckOk"] = true
        client.params?.let { body["params"] = it }
        if (client.scrambled) {
            val sts = YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
            body["playbackContext"] = JsonObject().apply {
                put("contentPlaybackContext", JsonObject().apply { put("signatureTimestamp", sts.toString()) })
            }
        }

        val request = Request.Builder()
            .url(PLAYER_URL)
            .header("User-Agent", client.userAgent)
            .apply { visitorData()?.let { header("X-Goog-Visitor-Id", it) } }
            .post(JsonWriter.string(body).toRequestBody(JSON))
            .build()
        val json = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("${client.name}: HTTP ${r.code}")
            JsonParser.`object`().from(r.body?.string().orEmpty())
        }

        val status = json.getObject("playabilityStatus")
        if (status.getString("status") != "OK") {
            val reason = status.getString("reason") ?: status.getString("status") ?: "unplayable"
            if (status.getString("status") == "LOGIN_REQUIRED") throw BlockedException("${client.name}: $reason")
            throw IOException("${client.name}: $reason")
        }

        val formats = json.getObject("streamingData").getArray("adaptiveFormats")
        val candidates = (0 until formats.size).map { formats.getObject(it) }
            .filter { it.getString("mimeType")?.startsWith("audio/") == true }
            .mapNotNull { f ->
                val url = playableUrl(videoId, f, client.scrambled) ?: return@mapNotNull null
                val bitrate = f.getInt("averageBitrate").takeIf { it > 0 } ?: f.getInt("bitrate")
                AudioCandidate(
                    url = url,
                    codec = codecOf(f.getString("mimeType")),
                    bitrate = bitrate,
                    progressive = true,
                    originalTrack = f.getObject("audioTrack").let { t -> t.isEmpty() || t.getBoolean("audioIsDefault") },
                )
            }
        val chosen = StreamSelector.choose(candidates) ?: throw IOException("${client.name}: no audio formats")
        return ResolvedAudio(chosen.url, chosen.codec, chosen.bitrate, mapOf("User-Agent" to client.userAgent), clock())
    }

    /** Direct URL, or a descrambled signatureCipher; then the throttling ("n") parameter is fixed. */
    private fun playableUrl(videoId: String, format: JsonObject, scrambled: Boolean): String? {
        var url = format.getString("url")
        if (url == null) {
            val cipher = format.getString("signatureCipher") ?: format.getString("cipher") ?: return null
            val parts = parseQuery(cipher)
            val base = parts["url"] ?: return null
            val s = parts["s"] ?: return null
            val sig = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, s)
            url = base + "&" + (parts["sp"] ?: "sig") + "=" + URLEncoder.encode(sig, "UTF-8")
        }
        if (scrambled || url.contains("&n=") || url.contains("?n=")) {
            url = try {
                YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
            } catch (_: Exception) {
                url // still playable, just possibly throttled
            }
        }
        return url
    }

    /** YouTube's visitor id (refreshed every 10 minutes), as youtube-source sends it. */
    @Synchronized
    private fun visitorData(): String? {
        if (visitorData != null && clock() - visitorFetchedAt < 10 * 60_000) return visitorData
        visitorData = runCatching {
            val body = """{"context":{"client":{"clientName":"ANDROID","clientVersion":"$ANDROID_VERSION","androidSdkVersion":30}}}"""
            val request = Request.Builder()
                .url("https://youtubei.googleapis.com/youtubei/v1/visitor_id")
                .header("User-Agent", "com.google.android.youtube/$ANDROID_VERSION (Linux; U; Android 11) gzip")
                .post(body.toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { r ->
                JsonParser.`object`().from(r.body?.string().orEmpty())
                    .getObject("responseContext").getString("visitorData")
            }
        }.getOrNull()
        visitorFetchedAt = clock()
        return visitorData
    }

    companion object {
        private const val PLAYER_URL = "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false"
        private const val ANDROID_VERSION = "19.44.38"
        private val JSON = "application/json".toMediaType()

        /** Samsung Tizen TV, the bot's patched client (yt-dlp's fix for "page needs to be reloaded"). */
        val TV = ClientSpec(
            name = "TVHTML5",
            version = "5.20260707",
            userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
            extraClientFields = mapOf("deviceMake" to "Samsung", "deviceModel" to "SmartTV", "osName" to "Tizen", "osVersion" to "2.4.0"),
            params = "2AMB",
            scrambled = true,
            embedded = false,
        )

        /** Oculus/Android VR app: plain URLs, no descrambling. */
        val ANDROID_VR = ClientSpec(
            name = "ANDROID_VR",
            version = "1.60.19",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            extraClientFields = mapOf("androidSdkVersion" to 32),
            scrambled = false,
            embedded = true,
        )

        /** Tried in this order (the bot's order, among clients that can play). */
        val CLIENTS = listOf(TV, ANDROID_VR)

        /** 'audio/webm; codecs="opus"' -> "opus". */
        fun codecOf(mimeType: String?): String? =
            mimeType?.substringAfter("codecs=\"", "")?.substringBefore('"')?.takeIf { it.isNotBlank() }

        fun parseQuery(q: String): Map<String, String> =
            q.split('&').mapNotNull { p ->
                val i = p.indexOf('=')
                if (i <= 0) null else p.substring(0, i) to URLDecoder.decode(p.substring(i + 1), "UTF-8")
            }.toMap()
    }
}
