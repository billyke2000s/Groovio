package com.groovio.app.playback

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class AudioFormatLabelTest {

    @Test fun `maps Media3 MIME types to familiar codec names`() {
        assertEquals("AAC", AudioFormatLabel.codecName("audio/mp4a-latm"))
        assertEquals("MP3", AudioFormatLabel.codecName("audio/mpeg"))
        assertEquals("OPUS", AudioFormatLabel.codecName("audio/opus"))
        assertEquals("FLAC", AudioFormatLabel.codecName("audio/flac"))
        assertEquals("ALAC", AudioFormatLabel.codecName("audio/alac"))
        assertEquals("VORBIS", AudioFormatLabel.codecName("audio/vorbis"))
    }

    @Test fun `unknown audio MIME falls back to subtype, non-audio is omitted`() {
        assertEquals("WAVPACK", AudioFormatLabel.codecName("audio/x-wavpack"))
        assertNull(AudioFormatLabel.codecName("video/avc"))
        assertNull(AudioFormatLabel.codecName(null))
        assertNull(AudioFormatLabel.codecName(""))
    }

    @Test fun `formats sample rates without trailing zeros`() {
        assertEquals("48kHz", AudioFormatLabel.sampleRate(48_000))
        assertEquals("44.1kHz", AudioFormatLabel.sampleRate(44_100))
        assertEquals("88.2kHz", AudioFormatLabel.sampleRate(88_200))
        assertEquals("192kHz", AudioFormatLabel.sampleRate(192_000))
        assertNull(AudioFormatLabel.sampleRate(Format.NO_VALUE))
        assertNull(AudioFormatLabel.sampleRate(0))
    }

    @Test fun `rounds bitrate to kilobits and omits unknown`() {
        assertEquals("160K", AudioFormatLabel.bitrate(160_000))
        assertEquals("128K", AudioFormatLabel.bitrate(127_800))
        assertNull(AudioFormatLabel.bitrate(Format.NO_VALUE))
    }

    @Test fun `builds the spec example and omits missing parts`() {
        assertEquals("OPUS · 160K · 48kHz", AudioFormatLabel.build("audio/opus", 160_000, 48_000))
        assertEquals("FLAC · 44.1kHz", AudioFormatLabel.build("audio/flac", Format.NO_VALUE, 44_100))
        assertNull(AudioFormatLabel.build(null, Format.NO_VALUE, Format.NO_VALUE))
    }

    @Test fun `uses average bitrate when no declared bitrate`() {
        val format = Format.Builder()
            .setSampleMimeType("audio/mp4a-latm")
            .setAverageBitrate(256_000)
            .setSampleRate(44_100)
            .build()
        assertEquals("AAC · 256K · 44.1kHz", AudioFormatLabel.from(format))
    }
}
