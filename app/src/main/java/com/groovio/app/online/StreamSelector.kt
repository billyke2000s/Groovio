package com.groovio.app.online

import java.util.Locale

/** One playable audio representation offered by a source. */
data class AudioCandidate(
    val url: String,
    /** RFC 6381 codec string, e.g. "opus", "mp4a.40.2", "flac". */
    val codec: String?,
    /** Average or declared bitrate in bit/s; 0 or less when unknown. */
    val bitrate: Int,
    /** Plain HTTP file (not DASH/HLS manifest). */
    val progressive: Boolean,
    /** False for dubbed/descriptive/secondary audio tracks. */
    val originalTrack: Boolean,
)

/**
 * Picks the best original audio representation (docs/AUDIO_QUALITY.md):
 * - lossless beats lossy; never ranks across codecs by bitrate alone;
 * - among lossy codecs, Opus beats AAC beats MP3 (better quality per bit at
 *   YouTube's tiers: Opus ~160K vs AAC ~128K);
 * - within a codec, the highest bitrate wins;
 * - the original audio track is preferred over dubs.
 */
object StreamSelector {

    enum class Family(val rank: Int) { LOSSLESS(4), OPUS(3), AAC(2), VORBIS(1), MP3(1), OTHER(0) }

    fun family(codec: String?): Family {
        val c = codec?.lowercase(Locale.ROOT)?.trim() ?: return Family.OTHER
        return when {
            c.startsWith("flac") || c.startsWith("alac") -> Family.LOSSLESS
            c.startsWith("opus") -> Family.OPUS
            // MP3 inside MP4 uses mp4a codec strings, so check it before AAC.
            c == "mp3" || c.startsWith("mp4a.40.34") || c.startsWith("mp4a.6b") -> Family.MP3
            c.startsWith("mp4a") || c == "aac" -> Family.AAC
            c.startsWith("vorbis") -> Family.VORBIS
            else -> Family.OTHER
        }
    }

    fun choose(candidates: List<AudioCandidate>): AudioCandidate? {
        val usable = candidates.filter { it.progressive && it.url.isNotBlank() }
        val pool = usable.filter { it.originalTrack }.ifEmpty { usable }
        return pool.maxWithOrNull(
            compareBy<AudioCandidate> { family(it.codec).rank }.thenBy { it.bitrate }
        )
    }
}
