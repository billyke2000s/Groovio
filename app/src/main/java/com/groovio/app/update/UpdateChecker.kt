package com.groovio.app.update

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A newer Groovio release with a downloadable APK. */
data class AvailableUpdate(val version: String, val apkUrl: String, val pageUrl: String)

/**
 * Checks Groovio's GitHub releases for a newer build. Releases are tagged "v0.1.<build>",
 * built automatically when the code (or the NewPipe Extractor version) changes.
 * Blocking network call: use a background thread.
 */
class UpdateChecker(
    private val currentBuild: Int,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    fun check(): AvailableUpdate? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            r.body?.string() ?: return null
        }
        return newest(JsonParser.array().from(body), currentBuild)
    }

    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/billyke2000s/Groovio/releases?per_page=10"

        /** "v0.1.42" -> 42; anything else -> null. */
        fun buildNumber(tag: String): Int? =
            Regex("""^v?\d+\.\d+\.(\d+)$""").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

        /** The newest non-draft release above [currentBuild] that has an APK attached. */
        fun newest(releases: JsonArray, currentBuild: Int): AvailableUpdate? =
            (0 until releases.size).asSequence()
                .map { releases.getObject(it) }
                .filter { !it.getBoolean("draft") }
                .mapNotNull { r ->
                    val tag = r.getString("tag_name") ?: return@mapNotNull null
                    val build = buildNumber(tag) ?: return@mapNotNull null
                    val assets = r.getArray("assets")
                    val apk = (0 until assets.size).map { assets.getObject(it) }
                        .firstOrNull { it.getString("name")?.endsWith(".apk") == true }
                        ?.getString("browser_download_url") ?: return@mapNotNull null
                    Triple(build, tag, AvailableUpdate(tag, apk, r.getString("html_url") ?: apk))
                }
                .filter { it.first > currentBuild }
                .maxByOrNull { it.first }
                ?.third
    }
}
