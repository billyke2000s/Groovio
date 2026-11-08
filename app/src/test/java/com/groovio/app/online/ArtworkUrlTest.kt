package com.groovio.app.online

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkUrlTest {

    @Test fun `upsizes YouTube Music art`() {
        // Shape taken from a real search result in the CI online smoke test.
        val small = "https://yt3.googleusercontent.com/abc=w120-h120-l90-rj"
        assertEquals("https://yt3.googleusercontent.com/abc=w544-h544-l90-rj", YouTubeMusic.largeArtwork(small))
    }

    @Test fun `leaves other hosts untouched`() {
        val ytimg = "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=x=w120-h120"
        assertEquals(ytimg, YouTubeMusic.largeArtwork(ytimg))
    }
}
