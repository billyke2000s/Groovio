package com.groovio.app.ui.player

import com.groovio.app.online.OnlineTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeVideoMatchTest {
    private fun video(title: String, duration: Long = 227_000L, uploader: String = "Inner Circle"): OnlineTrack =
        OnlineTrack(id = "video-id", title = title, artist = uploader,
            durationMs = duration, thumbnailUrl = null)

    @Test fun officialMatchingEditIsEligible() {
        assertTrue(LandscapeVideoMatch.accepts(
            "Bad Boys (Theme From Cops)", "Inner Circle", 225_000L,
            video("Inner Circle - Bad Boys (Official Music Video)")))
    }

    @Test fun remixOrLiveVersionNeverReplacesTheTrackVisuals() {
        for (qualifier in listOf("Live", "Remix", "Cover", "Lyric", "Visualizer", "Audio")) {
            assertFalse(LandscapeVideoMatch.accepts("Bad Boys", "Inner Circle", 227_000L,
                video("Inner Circle - Bad Boys ($qualifier Official Video)")))
        }
    }

    @Test fun mismatchedDurationFallsBackToArtwork() {
        assertFalse(LandscapeVideoMatch.accepts("Bad Boys", "Inner Circle", 227_000L,
            video("Inner Circle - Bad Boys (Official Music Video)", duration = 241_000L)))
    }

    @Test fun unrelatedArtistFallsBackToArtwork() {
        assertFalse(LandscapeVideoMatch.accepts("Bad Boys", "Inner Circle", 227_000L,
            video("Other Band - Bad Boys (Official Video)", uploader = "Other Band")))
    }

    @Test fun missingVideoDurationDoesNotGuessSync() {
        assertFalse(LandscapeVideoMatch.accepts("Bad Boys", "Inner Circle", 227_000L,
            video("Inner Circle - Bad Boys (Official Video)", duration = 0L)))
    }
}
