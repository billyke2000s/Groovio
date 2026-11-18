package com.groovio.app.offline

import android.content.Context
import androidx.work.*
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One bounded background job per song; WorkManager keeps pending work across restarts. */
class AudioDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as GroovioApplication
        val requestedId = inputData.getString("id")
        if (!app.settings.downloadsEnabled.value || app.settings.offlineOnly.value) return@withContext Result.failure()
        val track = if (requestedId != null) {
            TrackEntity(requestedId, inputData.getString("title") ?: "Song", inputData.getString("artist"), inputData.getString("art"))
        } else {
            val title = inputData.getString("queryTitle") ?: return@withContext Result.failure()
            try {
                val match = app.spotify.match(com.groovio.app.online.SpotifyTrack(title,
                    inputData.getString("queryArtist"), inputData.getLong("duration", 0L).takeIf { it > 0 }, null))
                    ?: return@withContext Result.failure(workDataOf("error" to "Couldn't find $title"))
                TrackEntity("yt:${match.id}", title, inputData.getString("queryArtist") ?: match.artist, match.thumbnailUrl)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { return@withContext if (runAttemptCount < 2) Result.retry() else Result.failure() }
        }
        val id = track.mediaId
        // The offline original is the download. A secondary MediaStore export is
        // best-effort and must never turn a playable song into a failed download.
        if (app.downloads.localFile(id) != null) {
            val exported = runCatching { app.downloads.publishToMusicFolder(track) }
            return@withContext Result.success(workDataOf(
                "mediaId" to id, "savedLocal" to true, "exportFailed" to exported.isFailure,
            ))
        }
        val temporary = File(app.downloads.file(id).path + ".${this@AudioDownloadWorker.id}.part")
        try {
            val audio = app.youtube.resolve(id.removePrefix("yt:"))
            AudioTransfer(client).save(audio, temporary, { isStopped }) { percent ->
                setProgress(workDataOf("percent" to percent))
            }
            app.downloads.complete(track, temporary) { isStopped }
            // Never fail or re-download an already saved track because the optional
            // second copy could not be written to Android's shared Music folder.
            val exported = runCatching { app.downloads.publishToMusicFolder(track, audio.codec) }
            Result.success(workDataOf(
                "mediaId" to id, "savedLocal" to true, "exportFailed" to exported.isFailure,
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2 && e is IOException) { app.youtube.invalidate(id.removePrefix("yt:")); Result.retry() }
            else Result.failure(workDataOf("error" to "Couldn't download ${track.title}. Try again when connected."))
        } finally { temporary.delete() }
    }
    companion object {
        private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(8, TimeUnit.MINUTES).build()
    }
}
