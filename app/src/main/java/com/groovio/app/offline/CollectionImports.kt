package com.groovio.app.offline

import android.content.Context
import androidx.work.*
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity
import com.groovio.app.online.SpotifyCollection
import com.groovio.app.online.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Private manifests keep original order and source metadata beyond screen/process lifetimes. */
class CollectionImports(private val context: Context) {
    private val directory = File(context.filesDir, "collection-imports").apply { mkdirs() }
    private val mutex = Mutex()
    private fun identity(c: SpotifyCollection) = c.sourceUrl ?: (c.type + ":" + c.name + ":" + c.tracks.joinToString { it.uri ?: "${it.title}|${it.artist}" })
    fun key(c: SpotifyCollection): String = MessageDigest.getInstance("SHA-256").digest(identity(c).toByteArray()).joinToString("") { "%02x".format(it) }
    fun file(key: String): File { require(key.matches(Regex("[a-f0-9]{64}"))); return File(directory, "$key.json") }
    fun read(key: String): JSONObject = JSONObject(file(key).readText())
    @Synchronized fun write(key: String, j: JSONObject) {
        val file = file(key); val tmp = File(file.path + ".part")
        tmp.writeText(j.toString()); check(tmp.renameTo(file)) { "Couldn't save import" }
    }
    suspend fun enqueue(c: SpotifyCollection) = mutex.withLock {
        val app = context.applicationContext as GroovioApplication
        if (!app.settings.downloadsEnabled.value || app.settings.offlineOnly.value) return@withLock
        val key = key(c)
        val previous = withContext(Dispatchers.IO) { runCatching { read(key) }.getOrNull() }
        val existingId = previous?.optLong("playlist")
        val exists = existingId != null && app.library.playlistName(existingId).first() != null
        if (!exists) {
            val playlist = app.library.createPlaylist(c.name)
            withContext(Dispatchers.IO) { write(key, JSONObject().put("playlist", playlist).put("name", c.name)
                .put("source", c.sourceUrl.orEmpty()).put("art", c.artworkUrl.orEmpty()).put("next", 0).put("matched", 0).put("missed", 0)
                .put("tracks", JSONArray().apply { c.tracks.forEach { t -> put(JSONObject().put("title", t.title)
                    .put("artist", t.artist.orEmpty()).put("duration", t.durationMs ?: 0).put("uri", t.uri.orEmpty())) } })) }
        }
        val request = OneTimeWorkRequestBuilder<CollectionImportWorker>()
            .setInputData(workDataOf("key" to key))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (app.settings.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .addTag("audio-download").addTag("collection-import").addTag("title:" + c.name.take(200)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("collection:$key", ExistingWorkPolicy.KEEP, request)
    }
}

class CollectionImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as GroovioApplication
        val key = inputData.getString("key") ?: return@withContext Result.failure()
        if (!app.settings.downloadsEnabled.value || app.settings.offlineOnly.value) return@withContext Result.failure()
        try {
            val j = app.collectionImports.read(key)
            val playlist = j.getLong("playlist")
            if (app.library.playlistName(playlist).first() == null) return@withContext Result.failure()
            val tracks = j.getJSONArray("tracks")
            for (index in j.getInt("next") until tracks.length()) {
                if (isStopped) return@withContext Result.retry()
                val t = tracks.getJSONObject(index)
                val track = SpotifyTrack(t.getString("title"), t.optString("artist").ifBlank { null }, t.optLong("duration").takeIf { it > 0 }, t.optString("uri"))
                val match = app.spotify.match(track)
                if (match != null) {
                    val stored = TrackEntity("yt:${match.id}", track.title, track.artist ?: match.artist, match.thumbnailUrl)
                    // Original position is the idempotency key, including any unavailable gaps.
                    app.library.importTrack(playlist, index, stored)
                    app.downloads.enqueue(listOf(stored))
                    j.put("matched", j.getInt("matched") + 1)
                } else j.put("missed", j.getInt("missed") + 1)
                j.put("next", index + 1); app.collectionImports.write(key, j)
                setProgress(workDataOf("matched" to j.getInt("matched"), "missed" to j.getInt("missed"), "total" to tracks.length()))
            }
            Result.success(workDataOf("matched" to j.getInt("matched"), "missed" to j.getInt("missed"), "total" to tracks.length()))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf("error" to "Import interrupted. Retry Save & play to continue.")) }
    }
}
