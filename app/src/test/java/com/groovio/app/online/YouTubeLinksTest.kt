package com.groovio.app.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinksTest {
    @Test fun supportsCommonVideoLinks() {
        val id = "WSh-6ITDPOQ"
        assertEquals(id, YouTubeLinks.videoId("https://youtu.be/$id?si=VgFZjQmOASjAghZd"))
        assertEquals(id, YouTubeLinks.videoId("https://www.youtube.com/watch?v=$id&t=12"))
        assertEquals(id, YouTubeLinks.videoId("https://music.youtube.com/watch?v=$id"))
        assertEquals(id, YouTubeLinks.videoId("https://youtube.com/shorts/$id"))
        assertEquals(id, YouTubeLinks.videoId("https://youtube.com/live/$id"))
    }

    @Test fun ignoresNonYouTubeOrInvalidLinks() {
        assertNull(YouTubeLinks.videoId("Daft Punk"))
        assertNull(YouTubeLinks.videoId("https://youtube.com/watch?v=invalid"))
        assertNull(YouTubeLinks.videoId("https://youtube.com.evil.test/watch?v=WSh-6ITDPOQ"))
        assertNull(YouTubeLinks.videoId("https://youtu.be/WSh-6ITDPOQ/extra"))
    }
}
