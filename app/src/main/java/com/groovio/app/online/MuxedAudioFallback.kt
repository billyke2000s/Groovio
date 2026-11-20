package com.groovio.app.online

/**
 * Safe last resort for uploads with no separate audio track. NewPipe's
 * `videoStreams` are muxed video+audio; video-only lives in a different list.
 * The playback service disables video decoding when using these files.
 */
internal object MuxedAudioFallback {
    data class Candidate(
        val url: String,
        val resolution: String?,
        val progressive: Boolean,
    )

    private val size = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)

    fun choose(streams: List<Candidate>): Candidate? =
        streams.asSequence()
            .filter { it.progressive && it.url.startsWith("https://") }
            // Use the lowest known resolution to minimise unnecessary video traffic.
            .minWithOrNull(compareBy<Candidate> {
                size.find(it.resolution.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
            })
}
