package com.groovio.app.online

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Public genre metadata only. Audio always remains on the existing YouTube source. */
class GenreMetadata(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
    private val endpoint: String = "https://musicbrainz.org/ws/2/",
    private val intervalMs: Long = 1100,
) {
    private val prefs = context.getSharedPreferences("artist_genres", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var lastRequest = 0L
    private suspend fun get(path: String, parameters: Map<String, String>): JSONObject {
        if (lastRequest > 0) delay((intervalMs - (System.nanoTime() - lastRequest) / 1_000_000).coerceAtLeast(0))
        lastRequest = System.nanoTime()
        val url = (endpoint + path).toHttpUrl().newBuilder().apply {
            parameters.forEach { (key, value) -> addQueryParameter(key, value) }; addQueryParameter("fmt", "json")
        }.build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "Groovio/0.1 (https://github.com/billyke2000s/Groovio)").build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Genre metadata unavailable" }
            JSONObject(response.body?.string() ?: error("No genre metadata"))
        }
    }
    suspend fun genre(artist: String): String? = lock.withLock {
        val key = artist.lowercase().trim()
        if (System.currentTimeMillis() - prefs.getLong("$key:at", 0) < 30L * 24 * 60 * 60 * 1000)
            return@withLock prefs.getString(key, "")?.ifBlank { null }
        val escaped = artist.replace("\\", "\\\\").replace("\"", "\\\"")
        val search = get("artist/", mapOf("query" to "artist:\"$escaped\"", "limit" to "5"))
        val matches = search.optJSONArray("artists")
        // Ambiguous same-name artists are left unclassified rather than guessed.
        val exact = matches?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.optInt("score") >= 90 && it.optString("name").equals(artist, true) } }.orEmpty()
        val id = exact.singleOrNull()?.optString("id")?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }
        val genre = id?.let { value ->
            val data = get("artist/$value", mapOf("inc" to "genres"))
            data.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optInt("count") > 0 }.maxByOrNull { it.optInt("count") }?.optString("name")?.take(80) }
        }
        prefs.edit().putString(key, genre.orEmpty()).putLong("$key:at", System.currentTimeMillis()).apply()
        genre
    }
}
