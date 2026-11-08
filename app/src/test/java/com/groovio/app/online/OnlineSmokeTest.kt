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

    @Test fun `artist page and album tracks load`() {
        assumeTrue("set GROOVIO_ONLINE_TESTS=1 to run", enabled)
        val yt = YouTubeMusic()
        val artist = yt.artist("Daft Punk")
        println("artist: ${artist.songs.size} songs, ${artist.albums.size} albums; albums: ${artist.albums.take(5).map { it.title }}")
        assertTrue("no artist songs", artist.songs.isNotEmpty())
        assertTrue("no artist albums", artist.albums.isNotEmpty())

        val album = artist.albums.first()
        val tracks = yt.albumTracks(album)
        println("album '${album.title}': ${tracks.size} tracks; first: ${tracks.firstOrNull()?.title}")
        assertTrue("album has no tracks", tracks.isNotEmpty())
    }

    @Test fun `home discovery shelves have covers`() {
        assumeTrue("set GROOVIO_ONLINE_TESTS=1 to run", enabled)
        val yt = YouTubeMusic()
        val hits = yt.searchSongs("top hits ${java.time.Year.now().value}")
        val albums = yt.searchAlbums("pop hits")
        println("discover: ${hits.size} hits (first: ${hits.firstOrNull()?.title}), ${albums.size} albums (first: ${albums.firstOrNull()?.title})")
        println("discover covers: ${hits.count { it.thumbnailUrl != null }}/${hits.size} songs, ${albums.count { it.thumbnailUrl != null }}/${albums.size} albums")
        assertTrue(hits.isNotEmpty() && albums.isNotEmpty())
        assertTrue(albums.all { it.thumbnailUrl != null })
    }

    /** Probe: does a public Spotify profile page list the user's public playlists? */
    @Test fun `probe Spotify profile page for public playlists`() {
        assumeTrue("set GROOVIO_ONLINE_TESTS=1 to run", enabled)
        for (url in listOf("https://open.spotify.com/user/spotify", "https://open.spotify.com/user/spotify/playlists")) {
            val request = Request.Builder().url(url).header("User-Agent", NewPipeDownloader.USER_AGENT).build()
            OkHttpClient().newCall(request).execute().use { r ->
                val html = r.body?.string().orEmpty()
                val playlists = Regex("spotify:playlist:[A-Za-z0-9]{22}|/playlist/[A-Za-z0-9]{22}").findAll(html).map { it.value.takeLast(22) }.toSet()
                val scripts = Regex("<script[^>]*id=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toList()
                println("profile probe $url: HTTP ${r.code}, ${html.length} chars, ${playlists.size} playlist ids, scripts=$scripts")
            }
        }
    }
}
