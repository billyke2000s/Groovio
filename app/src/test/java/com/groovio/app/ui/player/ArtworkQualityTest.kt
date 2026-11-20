package com.groovio.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkQualityTest {
    @Test fun googleHostedCoverCanRequest1080Pixels() {
        assertEquals(
            "https://yt3.googleusercontent.com/abc=w1080-h1080-l90-rj",
            ArtworkQuality.highResolutionUrl("https://yt3.googleusercontent.com/abc=w544-h544-l90-rj"),
        )
    }

    @Test fun youtubeThumbnailRequestsRealHigherResolutionVariant() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
            ArtworkQuality.highResolutionUrl("https://i.ytimg.com/vi/abc/hqdefault.jpg"),
        assertEquals("https://i.ytimg.com/vi/abc/maxresdefault.jpg",
            ArtworkQuality.highResolutionUrl("https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=x=w120-h120"))
        )
        assertEquals(
            "https://i.ytimg.com/vi_webp/abc/maxresdefault.webp",
            ArtworkQuality.highResolutionUrl("https://i.ytimg.com/vi_webp/abc/hqdefault.webp"),
        )
    }

    @Test fun unknownSourcesAreNotInvented() {
        assertNull(ArtworkQuality.highResolutionUrl("https://i.scdn.co/image/abc123"))
        assertNull(ArtworkQuality.highResolutionUrl("file:///storage/song.jpg"))
        assertNull(ArtworkQuality.highResolutionUrl("https://other-site.example/vi/abc/hqdefault.jpg"))
    }

    @Test fun alreadyHighResolutionDoesNotCauseExtraDownload() {
        assertNull(ArtworkQuality.highResolutionUrl("https://i.ytimg.com/vi/abc/maxresdefault.jpg"))
        assertNull(ArtworkQuality.highResolutionUrl("https://yt3.googleusercontent.com/abc=w1080-h1080"))
    }
}
