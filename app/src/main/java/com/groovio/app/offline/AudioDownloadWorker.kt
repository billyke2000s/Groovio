package com.groovio.app.offline

import android.content.Context
import androidx.work.*
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        if (app.downloads.localFile(id) != null) return@withContext Result.success()
        val temporary = File(app.downloads.file(id).path + ".${this@AudioDownloadWorker.id}.part")
        try {
            val audio = app.youtube.resolve(id.removePrefix("yt:"))
            val request = Request.Builder().url(audio.url).apply { audio.headers.forEach { (k, v) -> header(k, v) } }.build()
            val call = client.newCall(request)
            // Cancelling the coroutine also interrupts a blocked HTTP read.
            val cancellation = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) { app.youtube.invalidate(id.removePrefix("yt:")); throw IOException("HTTP ${response.code}") }
                    val body = response.body ?: throw IOException("No audio returned")
                    val type = body.contentType()?.toString().orEmpty()
                    if (type.contains("text/") || type.contains("json")) throw IOException("Response wasn't audio")
                    val expected = body.contentLength()
                    if (expected > MAX_BYTES) throw IOException("Song exceeds 512 MB")
                    var count = 0L
                    body.byteStream().use { source -> temporary.outputStream().use { dest ->
                        val buffer = ByteArray(64 * 1024)
                        var reported = -1
                        while (true) {
                            if (isStopped) throw CancellationException("Cancelled")
                            val size = source.read(buffer)
                            if (size < 0) break
                            count += size
                            if (count > MAX_BYTES) throw IOException("Song exceeds 512 MB")
                            dest.write(buffer, 0, size)
                            val percent = if (expected > 0) ((count * 100) / expected).toInt() else 0
                            if (percent != reported) { setProgress(workDataOf("percent" to percent)); reported = percent }
                        }
                    } }
                    if (count == 0L || (expected >= 0 && count != expected)) throw IOException("Incomplete audio")
                }
                app.downloads.complete(track, temporary) { isStopped }
                Result.success()
            } finally { cancellation?.dispose() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2 && e is IOException) Result.retry()
            else Result.failure(workDataOf("error" to "Couldn't download ${track.title}. Try again when connected."))
        } finally { temporary.delete() }
    }
    companion object {
        private const val MAX_BYTES = 512L * 1024 * 1024
        private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(8, TimeUnit.MINUTES).build()
    }
}
