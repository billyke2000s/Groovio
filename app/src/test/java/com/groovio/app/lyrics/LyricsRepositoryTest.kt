package com.groovio.app.lyrics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRepositoryTest {
    @Test fun parsesMultipleTimestampsAndMetadata() {
        val rows = LrcLyrics.parse("[ar:Artist]\n[00:20.5]Second\n[00:01.25][00:03.000]First\n[00:10.00]\n")
        assertEquals(listOf(1250L, 3000L, 20500L), rows.map { it.timeMs })
        assertEquals(listOf("First", "First", "Second"), rows.map { it.text })
        assertEquals(-1, LrcLyrics.activeIndex(rows, 0))
        assertEquals(1, LrcLyrics.activeIndex(rows, 4000))
        assertEquals(2, LrcLyrics.activeIndex(rows, 20500))
    }
    @Test fun acceptsMatchingLyricsOnly() {
        val repository = LyricsRepository(File("unused"))
        val track = LyricsTrack("yt:abc", "Hello", "Singer", 120_000L)
        val source = """{"trackName":"Hello","artistName":"Singer","duration":120,
            "syncedLyrics":"[00:01.00]One","instrumental":false}"""
        assertEquals("One", repository.decode(source, track)?.synced?.single()?.text)
        assertNull(repository.decode(source.replace("Singer", "Someone else"), track))
        assertNull(repository.decode(source.replace("120,", "150,"), track))
    }
    @Test fun supportsPlainAndInstrumentalResponses() {
        val repository = LyricsRepository(File("unused"))
        val track = LyricsTrack("yt:abc", "Track", "Singer", null)
        val plain = """{"trackName":"Track","artistName":"Singer","duration":100,
            "plainLyrics":"First line\nSecond line","instrumental":false}"""
        assertEquals("First line\nSecond line", repository.decode(plain, track)?.plain)
        val instrumental = """{"trackName":"Track","artistName":"Singer","instrumental":true}"""
        assertTrue(repository.decode(instrumental, track)?.instrumental == true)
    }
}
