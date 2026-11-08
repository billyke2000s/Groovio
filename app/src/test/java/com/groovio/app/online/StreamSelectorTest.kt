package com.groovio.app.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSelectorTest {

    private fun c(url: String, codec: String?, kbps: Int, progressive: Boolean = true, original: Boolean = true) =
        AudioCandidate(url, codec, kbps * 1000, progressive, original)

    @Test fun `prefers highest Opus over AAC on typical YouTube tiers`() {
        val chosen = StreamSelector.choose(listOf(
            c("aac128", "mp4a.40.2", 128),
            c("opus50", "opus", 50),
            c("opus160", "opus", 160),
            c("aac48", "mp4a.40.5", 48),
        ))
        assertEquals("opus160", chosen?.url)
    }

    @Test fun `lossless beats lossy regardless of bitrate`() {
        val chosen = StreamSelector.choose(listOf(c("opus", "opus", 160), c("flac", "flac", 0)))
        assertEquals("flac", chosen?.url)
    }

    @Test fun `prefers original audio track over dubs`() {
        val chosen = StreamSelector.choose(listOf(
            c("dub", "opus", 160, original = false),
            c("orig", "mp4a.40.2", 128),
        ))
        assertEquals("orig", chosen?.url)
    }

    @Test fun `falls back to dubs when nothing else exists`() {
        assertEquals("dub", StreamSelector.choose(listOf(c("dub", "opus", 160, original = false)))?.url)
    }

    @Test fun `ignores manifests and empty urls`() {
        assertNull(StreamSelector.choose(listOf(c("dash", "opus", 160, progressive = false), c("", "opus", 160))))
    }

    @Test fun `mp3 in mp4 is not mistaken for AAC`() {
        assertEquals(StreamSelector.Family.MP3, StreamSelector.family("mp4a.40.34"))
        assertEquals(StreamSelector.Family.AAC, StreamSelector.family("mp4a.40.2"))
    }

    @Test fun `data saver picks the smallest efficient stream`() {
        val chosen = StreamSelector.choose(listOf(
            c("aac128", "mp4a.40.2", 128),
            c("opus50", "opus", 50),
            c("opus160", "opus", 160),
            c("flac", "flac", 900),
        ), dataSaver = true)
        assertEquals("opus50", chosen?.url)
    }
}
