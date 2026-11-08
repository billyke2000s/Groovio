package com.groovio.app.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SpotifyLinksTest {

    @Test fun `parses open spotify links including intl and query`() {
        assertEquals(SpotifyRef("track", "69kOkLUCkxIZYexIgSG8rq"),
            SpotifyLinks.parse("https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq?si=abc123"))
        assertEquals(SpotifyRef("album", "4m2880jivSbbyEGAKfITCa"),
            SpotifyLinks.parse("https://open.spotify.com/intl-de/album/4m2880jivSbbyEGAKfITCa"))
        assertEquals(SpotifyRef("playlist", "37i9dQZF1DXcBWIGoYBM5M"),
            SpotifyLinks.parse("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"))
        assertEquals("https://open.spotify.com/embed/album/abc", SpotifyRef("album", "abc").embedUrl)
    }

    @Test fun `rejects non-music and non-spotify links`() {
        assertNull(SpotifyLinks.parse("https://open.spotify.com/episode/abc"))
        assertNull(SpotifyLinks.parse("https://example.com/track/abc"))
        assertNull(SpotifyLinks.parse("daft punk get lucky"))
    }

    @Test fun `finds a link inside shared text`() {
        val shared = "Listen to Get Lucky on Spotify: https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq?si=x."
        assertEquals("https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq?si=x", SpotifyLinks.findLink(shared))
        assertTrue(SpotifyLinks.isShortLink("https://spotify.link/AbCdEf"))
        assertNull(SpotifyLinks.findLink("daft punk"))
    }

    @Test fun `reads an album from the __NEXT_DATA__ embed page`() {
        val json = """{"props":{"pageProps":{"state":{"data":{"entity":{
            "type":"album","name":"Random Access Memories","uri":"spotify:album:4m2880jivSbbyEGAKfITCa",
            "trackList":[
              {"uri":"spotify:track:1","title":"Give Life Back to Music","subtitle":"Daft Punk","duration":274000},
              {"uri":"spotify:track:2","title":"Get Lucky","subtitle":"Daft Punk, Pharrell Williams, Nile Rodgers","duration":369000},
              {"uri":"spotify:episode:3","title":"A podcast","subtitle":"Someone","duration":1}
            ]}}}}}}"""
        val html = """<html><body><script id="__NEXT_DATA__" type="application/json">$json</script></body></html>"""
        val c = SpotifyLinks.parseEmbed(html)
        assertEquals("Random Access Memories", c.name)
        assertEquals("album", c.type)
        assertEquals(listOf("Give Life Back to Music", "Get Lucky"), c.tracks.map { it.title })
        assertEquals("Daft Punk, Pharrell Williams, Nile Rodgers", c.tracks[1].artist)
        assertEquals(369_000L, c.tracks[1].durationMs)
    }

    @Test fun `reads a single track with artists array from a base64 resource script`() {
        val json = """{"type":"track","name":"Get Lucky","uri":"spotify:track:69kOkLUCkxIZYexIgSG8rq",
            "artists":[{"name":"Daft Punk"},{"name":"Pharrell Williams"}],"duration":248413}"""
        val b64 = Base64.getEncoder().encodeToString(json.toByteArray())
        val html = """<html><body><script id="resource" type="application/json">$b64</script></body></html>"""
        val c = SpotifyLinks.parseEmbed(html)
        assertEquals("track", c.type)
        assertEquals(1, c.tracks.size)
        assertEquals("Daft Punk, Pharrell Williams", c.tracks[0].artist)
        assertEquals(248_413L, c.tracks[0].durationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown page shape is a clear error`() {
        SpotifyLinks.parseEmbed("<html><body>nothing here</body></html>")
    }
}
