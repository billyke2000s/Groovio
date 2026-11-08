package com.groovio.app.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Format.NO_VALUE
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/**
 * Builds the inline audio label shown after a song title, e.g. "OPUS · 160K · 48kHz".
 *
 * Rules (see docs/AUDIO_QUALITY.md): report only what the selected format declares,
 * omit unknown values, never infer quality from a file extension.
 */
object AudioFormatLabel {

    private val codecNames = mapOf(
        "audio/opus" to "OPUS",
        "audio/mp4a-latm" to "AAC",
        "audio/mpeg" to "MP3",
        "audio/mpeg-l1" to "MP1",
        "audio/mpeg-l2" to "MP2",
        "audio/flac" to "FLAC",
        "audio/alac" to "ALAC",
        "audio/vorbis" to "VORBIS",
        "audio/raw" to "PCM",
        "audio/ac3" to "AC3",
        "audio/eac3" to "EAC3",
        "audio/eac3-joc" to "EAC3",
        "audio/ac4" to "AC4",
        "audio/true-hd" to "TRUEHD",
        "audio/vnd.dts" to "DTS",
        "audio/vnd.dts.hd" to "DTS-HD",
        "audio/3gpp" to "AMR-NB",
        "audio/amr-wb" to "AMR-WB",
        "audio/g711-alaw" to "G711",
        "audio/g711-mlaw" to "G711",
    )

    /** Codec name for a sample MIME type, or null if unknown. */
    fun codecName(sampleMimeType: String?): String? {
        if (sampleMimeType.isNullOrBlank()) return null
        val mime = sampleMimeType.lowercase(Locale.ROOT)
        codecNames[mime]?.let { return it }
        // Unknown but well-formed audio MIME: show its subtype rather than guess.
        return mime.takeIf { it.startsWith("audio/") }
            ?.substringAfter('/')
            ?.removePrefix("x-")
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
    }

    /** "160K" for 160 000 bit/s; null when unknown or non-positive. */
    fun bitrate(bitsPerSecond: Int): String? =
        bitsPerSecond.takeIf { it > 0 }?.let { "${Math.round(it / 1000.0)}K" }

    /** "48kHz", "44.1kHz", "88.2kHz"; null when unknown or non-positive. */
    fun sampleRate(hz: Int): String? {
        if (hz <= 0) return null
        val khz = hz / 1000.0
        val text = if (hz % 1000 == 0) {
            (hz / 1000).toString()
        } else {
            String.format(Locale.ROOT, "%.1f", khz).removeSuffix(".0")
        }
        return "${text}kHz"
    }

    /** Joins the known parts; returns null when nothing is known. */
    fun build(sampleMimeType: String?, bitrate: Int, sampleRate: Int): String? =
        listOfNotNull(codecName(sampleMimeType), bitrate(bitrate), sampleRate(sampleRate))
            .joinToString(" · ")
            .ifEmpty { null }

    /**
     * Label from a Media3 [Format]; prefers the declared bitrate, then the average.
     * Media3 marks the bitrate fields as unstable API; re-check on Media3 upgrades.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun from(format: Format): String? {
        val declared = when {
            format.bitrate != NO_VALUE -> format.bitrate
            format.averageBitrate != NO_VALUE -> format.averageBitrate
            else -> NO_VALUE
        }
        return build(format.sampleMimeType, declared, format.sampleRate)
    }

    /** The format of the audio track actually selected for playback, if any. */
    fun selectedAudioFormat(tracks: Tracks): Format? {
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO || !group.isSelected) continue
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) return group.getTrackFormat(i)
            }
        }
        return null
    }
}
