package com.groovio.app.offline

import com.groovio.app.online.ResolvedAudio
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Range requests avoid saving a server's first partial response as a complete song. */
class AudioTransfer(private val client: OkHttpClient, private val chunkBytes: Long = 1024 * 1024,
    private val maxBytes: Long = 512L * 1024 * 1024) {
    suspend fun save(audio: ResolvedAudio, file: File, stopped: () -> Boolean,
        progress: suspend (Int) -> Unit) = coroutineScope {
        val started = System.nanoTime()
        var count = 0L
        var total: Long? = null
        var reported = -1
        file.outputStream().use { dest ->
            do {
                ensureActive()
                if (stopped()) throw CancellationException("Cancelled")
                if (System.nanoTime() - started > 8L * 60 * 1_000_000_000) throw IOException("Download timed out")
                val request = Request.Builder().url(audio.url)
                    .apply { audio.headers.forEach { (k, v) -> header(k, v) } }
                    .header("Range", "bytes=$count-${count + chunkBytes - 1}").build()
                val call = client.newCall(request)
                val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body ?: throw IOException("No audio returned")
                        val type = body.contentType()?.toString().orEmpty()
                        if (type.contains("text/") || type.contains("json")) throw IOException("Response wasn't audio")
                        val range = if (response.code == 206) parseRange(response.header("Content-Range")) else null
                        if (response.code == 206 && range == null) throw IOException("Invalid partial response")
                        if (range != null) {
                            if (range.first != count || (total != null && total != range.third)) throw IOException("Wrong audio range")
                            total = range.third
                        } else {
                            if (count != 0L) throw IOException("Server ignored a continuation range")
                            total = body.contentLength().takeIf { it >= 0 }
                        }
                        if ((total ?: 0) > maxBytes) throw IOException("Song exceeds 512 MB")
                        val expected = range?.let { it.second - it.first + 1 } ?: body.contentLength()
                        var received = 0L
                        body.byteStream().use { source ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                if (stopped()) throw CancellationException("Cancelled")
                                val size = source.read(buffer)
                                if (size < 0) break
                                count += size; received += size
                                if (count > maxBytes || (total != null && count > total!!)) throw IOException("Audio exceeds expected length")
                                dest.write(buffer, 0, size)
                                val percent = total?.takeIf { it > 0 }?.let { ((count * 100) / it).toInt() } ?: 0
                                if (percent != reported) { progress(percent); reported = percent }
                            }
                        }
                        if (received == 0L || (expected >= 0 && received != expected)) throw IOException("Incomplete audio")
                        if (range == null) total = count
                    }
                } finally { cancellation.cancelAndJoin() }
            } while (count < (total ?: count))
        }
    }
    companion object {
        fun parseRange(header: String?): Triple<Long, Long, Long>? {
            val values = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header.orEmpty())?.groupValues ?: return null
            val start = values[1].toLongOrNull() ?: return null
            val end = values[2].toLongOrNull() ?: return null
            val total = values[3].toLongOrNull() ?: return null
            return Triple(start, end, total).takeIf { start >= 0 && end >= start && end < total }
        }
    }
}
