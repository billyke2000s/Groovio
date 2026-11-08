package com.groovio.app.online

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

/**
 * Real-network check of search -> resolve -> fetch audio bytes.
 * Skipped unless GROOVIO_ONLINE_TESTS=1 (CI runs it in a separate, non-blocking job).
 * A failure from a datacenter IP does not prove phones fail: YouTube often blocks servers.
 */
class OnlineSmokeTest {

    companion object {
        private val enabled = System.getenv("GROOVIO_ONLINE_TESTS") == "1"

        @JvmStatic
        @BeforeClass
        fun init() {
            if (enabled) NewPipe.init(NewPipeDownloader(), Localization.fromLocale(Locale.UK), ContentCountry("GB"))
        }
    }

    @Test fun `search, resolve and stream the first song`() {
        assumeTrue("set GROOVIO_ONLINE_TESTS=1 to run", enabled)
        val yt = YouTubeMusic()

        val results = yt.searchSongs("Daft Punk Get Lucky")
        println("search: ${results.size} results; first: ${results.firstOrNull()}")
        assertFalse("no search results", results.isEmpty())

        val track = results.first()
        val audio = yt.resolve(track.id)
        println("resolved ${track.id}: codec=${audio.codec} bitrate=${audio.bitrate} headers=${audio.headers.keys}")
        assertTrue(audio.url.startsWith("https://"))

        val request = Request.Builder().url(audio.url).header("Range", "bytes=0-65535")
            .apply { audio.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            val bytes = response.body?.bytes()?.size ?: 0
            println("stream fetch: HTTP ${response.code}, $bytes bytes")
            assertTrue("HTTP ${response.code}", response.code == 200 || response.code == 206)
            assertTrue("empty body", bytes > 0)
        }
    }

    @Test fun `Spotify album link is read and its songs matched on YouTube`() {
        assumeTrue("set GROOVIO_ONLINE_TESTS=1 to run", enabled)
        val importer = SpotifyImporter()
        val album = importer.load("https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa")
        println("spotify: ${album.type} '${album.name}' with ${album.tracks.size} tracks; first: ${album.tracks.firstOrNull()}")
        assertTrue("no tracks", album.tracks.isNotEmpty())

        val sample = album.tracks.take(3)
        val matches = sample.map { it to importer.match(it) }
        matches.forEach { (s, m) -> println("match: '${s.title}' -> ${m?.let { "${it.title} / ${it.artist} (${it.id})" }}") }
        assertTrue("fewer than 2 of 3 songs matched", matches.count { it.second != null } >= 2)
    }
}
