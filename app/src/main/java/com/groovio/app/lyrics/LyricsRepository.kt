package com.groovio.app.lyrics

import com.grack.nanojson.JsonParser
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LyricsTrack(val mediaId: String, val title: String, val artist: String?, val durationMs: Long?)
data class TimedLyric(val timeMs: Long, val text: String)
data class LyricsDocument(val synced: List<TimedLyric>, val plain: String?, val instrumental: Boolean)

sealed interface LyricsLookup {
    data class Found(val document: LyricsDocument) : LyricsLookup
    data object Missing : LyricsLookup
    data class Failed(val message: String) : LyricsLookup
}

/** Read standard LRC line timestamps, including multiple timestamps per line. */
object LrcLyrics {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
    fun parse(input: String): List<TimedLyric> {
        val lines = mutableListOf<TimedLyric>()
        for (row in input.lineSequence()) {
            val matches = timestamp.findAll(row).toList()
            if (matches.isEmpty()) continue
            val lyric = timestamp.replace(row, "").trim()
            if (lyric.isEmpty()) continue
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                if (seconds >= 60) continue
                val fraction = match.groupValues[3]
                val millis = if (fraction.isEmpty()) 0L
                    else fraction.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                lines.add(TimedLyric((minutes * 60 + seconds) * 1000 + millis, lyric))
            }
        }
        return lines.distinct().sortedBy { it.timeMs }
    }
    fun activeIndex(lines: List<TimedLyric>, positionMs: Long): Int =
        lines.indexOfLast { it.timeMs <= positionMs }
}

/** On-demand LRCLIB lookup; strict metadata check avoids showing lyrics for a different song. */
class LyricsRepository(
    private val directory: File,
    private val endpoint: HttpUrl = "https://lrclib.net/api/get".toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build(),
) {
    private val requestLock = Mutex()
    private var previousRequestMs = 0L

    suspend fun load(track: LyricsTrack): LyricsLookup = withContext(Dispatchers.IO) {
        if (track.title.isBlank() || track.artist.isNullOrBlank() ||
            track.artist.equals("Unknown artist", ignoreCase = true)) {
            return@withContext LyricsLookup.Missing
        }
        val identity = listOf(track.mediaId, track.title, track.artist, track.durationMs ?: 0L).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(directory, "$digest.json")
        runCatching { if (file.isFile) decode(file.readText(), track) else null }
            .getOrNull()?.let { return@withContext LyricsLookup.Found(it) }

        requestLock.withLock {
            val elapsed = System.currentTimeMillis() - previousRequestMs
            if (elapsed in 0..349) delay(350 - elapsed)
            previousRequestMs = System.currentTimeMillis()
            val url = endpoint.newBuilder()
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", track.artist)
                .apply {
                    val duration = track.durationMs?.div(1000)
                    if (duration != null && duration in 1..3600)
                        addQueryParameter("duration", duration.toString())
                }.build()
            val request = Request.Builder().url(url)
                .header("Lrclib-Client", "Groovio/0.1 (https://github.com/billyke2000s/Groovio)")
                .header("Accept", "application/json")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        404 -> LyricsLookup.Missing
                        429 -> LyricsLookup.Failed("Lyrics service is busy. Please try again later.")
                        200 -> {
                            val body = response.body?.string() ?: return@use LyricsLookup.Missing
                            val document = decode(body, track) ?: return@use LyricsLookup.Missing
                            runCatching { directory.mkdirs(); file.writeText(body) }
                            LyricsLookup.Found(document)
                        }
                        else -> LyricsLookup.Failed("Lyrics couldn't be loaded (" + response.code + ").")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                LyricsLookup.Failed("Couldn't connect to lyrics. Check your connection or try again.")
            } catch (_: Exception) {
                LyricsLookup.Failed("Lyrics couldn't be read. Please try again.")
            }
        }
    }

    internal fun decode(json: String, track: LyricsTrack): LyricsDocument? {
        val record = JsonParser.`object`().from(json)
        val title = record.getString("trackName") ?: record.getString("name") ?: return null
        val artist = record.getString("artistName") ?: return null
        if (normalise(title) != normalise(track.title) ||
            normalise(artist) != normalise(track.artist.orEmpty())) return null
        val duration = record.getDouble("duration")
        val expected = track.durationMs
        if (expected != null && expected > 0L && duration > 0 &&
            abs(expected - (duration * 1000).toLong()) > 5000) return null
        val instrumental = record.getBoolean("instrumental")
        val synced = record.getString("syncedLyrics")?.let(LrcLyrics::parse).orEmpty()
        val plain = record.getString("plainLyrics")?.takeIf { it.isNotBlank() }
        if (!instrumental && synced.isEmpty() && plain == null) return null
        return LyricsDocument(synced, plain, instrumental)
    }

    private fun normalise(value: String): String =
        Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(Regex("""\p{M}"""), "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
}
