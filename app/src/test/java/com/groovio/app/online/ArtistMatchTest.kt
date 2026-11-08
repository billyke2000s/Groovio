package com.groovio.app.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistMatchTest {
    @Test fun `same artist ignores case, accents and features`() {
        assertTrue(YouTubeMusic.sameArtist("Daft Punk", "daft punk"))
        assertTrue(YouTubeMusic.sameArtist("Beyoncé", "Beyonce"))
        assertTrue(YouTubeMusic.sameArtist("Daft Punk", "Daft Punk & Pharrell Williams"))
        assertTrue(YouTubeMusic.sameArtist("Daft Punk, Pharrell Williams", "Pharrell Williams"))
    }

    @Test fun `different artists do not match`() {
        assertFalse(YouTubeMusic.sameArtist("Daft Punk", "Justice"))
        assertFalse(YouTubeMusic.sameArtist("Daft Punk", null))
        assertFalse(YouTubeMusic.sameArtist("Daft Punk", ""))
    }
}
