package com.groovio.app.online

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Checks the actual media CDN before handing a long-form source to Media3.
 * Some YouTube URLs serve the first range but reject subsequent ranges with
 * HTTP 403, so a successful extraction or first-byte check is not enough.
 * This performs two tiny GET requests; never downloads or transcodes a recording.
 */
internal class StreamProbe(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    data class Result(val playable: Boolean, val httpStatus: Int? = null, val failedOffset: Long? = null)

    fun verify(source: ResolvedAudio): Result {
        // A spoken recording is many megabytes; check both its start and a later seek.
        for (offset in longArrayOf(0L, 3_145_728L)) {
            val request = Request.Builder().url(source.url)
                .header("Range", "bytes=$offset-${offset + 1023}")
                .header("Accept-Encoding", "identity")
                .apply { source.headers.forEach { (key, value) -> header(key, value) } }
                .get().build()
            val result = try {
                http.newCall(request).execute().use { response ->
                    if (response.code !in 200..299 || (offset > 0 && response.code != 206)) {
                        Result(false, response.code, offset)
                    } else if (response.body?.byteStream()?.read() == -1 || response.body == null) {
                        Result(false, response.code, offset)
                    } else Result(true, response.code)
                }
            } catch (_: Exception) {
                Result(false, failedOffset = offset)
            }
            if (!result.playable) return result
        }
        return Result(true)
    }
}

/**
 * Testable source failover: a rejected signed URL is never returned again.
 * Accepting one candidate must mean a real media request was successful.
 */
internal class SpokenSourceSelector(
    private val probe: (ResolvedAudio) -> StreamProbe.Result,
    private val rejectedUrls: MutableSet<String>,
) {
    var lastHttpCode: Int? = null
        private set

    fun tryCandidate(candidate: ResolvedAudio): ResolvedAudio? {
        if (candidate.url in rejectedUrls) return null
        val result = probe(candidate)
        if (result.playable) return candidate
        if (result.httpStatus != null && result.httpStatus !in 200..299) lastHttpCode = result.httpStatus
        rejectedUrls.add(candidate.url)
        return null
    }
}

/** Failure returned only after every eligible source failed a real HTTP byte-range check. */
class SpokenSourceRejectedException(val status: Int?) : java.io.IOException(
    if (status != null) "No playable spoken recording source (HTTP $status)"
    else "No spoken recording source passed media playback checks"
)
