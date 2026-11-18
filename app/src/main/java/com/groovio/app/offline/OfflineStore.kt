package com.groovio.app.offline

import android.content.Context
import androidx.work.*
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Completed downloads live in filesDir, never an evictable cache. Identity stays yt:<id>. */
class OfflineStore(private val context: Context) {
    private val directory = File(context.filesDir, "downloads").apply { mkdirs() }
    private val work get() = WorkManager.getInstance(context)
    private val mutable = MutableStateFlow(readTracks())
    val tracks = mutable.asStateFlow()
    val jobs get() = work.getWorkInfosByTagFlow("audio-download")
    private fun key(id: String) = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
    fun file(id: String) = File(directory, key(id) + ".audio")
    private fun metadata(id: String) = File(directory, key(id) + ".json")
    fun localFile(id: String): File? = file(id).takeIf { it.length() > 0 && metadata(id).isFile }
    private fun readTracks(): List<TrackEntity> = directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { f ->
        runCatching {
            val j = JSONObject(f.readText())
            TrackEntity(j.getString("id"), j.getString("title"), j.optString("artist").ifBlank { null }, j.optString("art").ifBlank { null })
                .takeIf { file(it.mediaId).length() > 0 }
        }.getOrNull()
    }.sortedBy { it.title.lowercase() }
    @Synchronized fun refresh() { mutable.value = readTracks() }
    fun enqueue(tracks: List<TrackEntity>) {
        val app = context.applicationContext as GroovioApplication
        if (!app.settings.downloadsEnabled.value || app.settings.offlineOnly.value) return
        tracks.distinctBy { it.mediaId }.filter { it.mediaId.startsWith("yt:") && localFile(it.mediaId) == null }.forEach { track ->
            val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
                .setInputData(workDataOf("id" to track.mediaId, "title" to track.title, "artist" to track.artist, "art" to track.artworkUri))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(
                    if (app.settings.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED
                ).setRequiresStorageNotLow(true).build())
                .addTag("audio-download").addTag(track.mediaId).addTag("title:" + track.title.take(200)).build()
            work.enqueueUniqueWork("download:" + track.mediaId, ExistingWorkPolicy.KEEP, request)
        }
    }
    /** Matching is durable too: closing the app won't abandon the rest of a Spotify playlist. */
    fun enqueueSpotify(tracks: List<com.groovio.app.online.SpotifyTrack>) {
        val app = context.applicationContext as GroovioApplication
        if (!app.settings.downloadsEnabled.value || app.settings.offlineOnly.value) return
        tracks.distinctBy { it.title to it.artist }.forEach { track ->
            val identity = key(track.title + "|" + track.artist.orEmpty() + "|" + track.durationMs)
            val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
                .setInputData(workDataOf("queryTitle" to track.title, "queryArtist" to track.artist, "duration" to (track.durationMs ?: 0L)))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(
                    if (app.settings.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED
                ).setRequiresStorageNotLow(true).build())
                .addTag("audio-download").addTag("title:" + track.title.take(200)).build()
            work.enqueueUniqueWork("match-download:$identity", ExistingWorkPolicy.KEEP, request)
        }
    }
    fun cancelAll() { work.cancelAllWorkByTag("audio-download") }
    suspend fun remove(track: TrackEntity) = withContext(Dispatchers.IO) {
        work.cancelUniqueWork("download:" + track.mediaId).result.get()
        // Worker checks cancellation before publishing; deletion waits on the same monitor.
        synchronized(this@OfflineStore) {
            metadata(track.mediaId).delete(); file(track.mediaId).delete(); refresh()
        }
    }
    suspend fun clear() = withContext(Dispatchers.IO) {
        work.cancelAllWorkByTag("audio-download").result.get()
        synchronized(this@OfflineStore) {
            directory.listFiles().orEmpty().forEach { it.delete() }
            refresh()
        }
    }
    @Synchronized fun complete(track: TrackEntity, temporary: File, cancelled: () -> Boolean) {
        if (cancelled()) throw java.io.IOException("Download cancelled")
        check(temporary.renameTo(file(track.mediaId))) { "Couldn't save audio" }
        val target = metadata(track.mediaId)
        val tmp = File(target.path + ".part")
        tmp.writeText(JSONObject().put("id", track.mediaId).put("title", track.title)
            .put("artist", track.artist ?: "").put("art", track.artworkUri ?: "").toString())
        check(tmp.renameTo(target)) { "Couldn't save download details" }
        refresh()
    }
    fun bytes(): Long = tracks.value.sumOf { file(it.mediaId).length() }
}
