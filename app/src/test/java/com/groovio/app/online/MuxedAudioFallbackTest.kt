package com.groovio.app.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuxedAudioFallbackTest {
    @Test fun usesSmallestProgressiveMuxedRepresentation() {
        val candidates = listOf(
            MuxedAudioFallback.Candidate("https://cdn.example/720", "720p", true),
            MuxedAudioFallback.Candidate("https://cdn.example/360", "360p", true),
            MuxedAudioFallback.Candidate("https://cdn.example/144", "144p", true),
        )
        assertEquals("https://cdn.example/144", MuxedAudioFallback.choose(candidates)?.url)
    }

    @Test fun ignoresUnplayableManifestsAndMissingUrls() {
        assertNull(MuxedAudioFallback.choose(listOf(
            MuxedAudioFallback.Candidate("https://cdn.example/dash", "144p", false),
            MuxedAudioFallback.Candidate("", "240p", true),
        )))
    }

    @Test fun unknownResolutionIsUsedOnlyWhenNoKnownSizeAvailable() {
        val unknown = MuxedAudioFallback.Candidate("https://cdn.example/unknown", null, true)
        val known = MuxedAudioFallback.Candidate("https://cdn.example/360", "360p", true)
        assertEquals(known, MuxedAudioFallback.choose(listOf(unknown, known)))
        assertEquals(unknown, MuxedAudioFallback.choose(listOf(unknown)))
    }
}
