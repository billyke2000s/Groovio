package com.groovio.app.online

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class OfflineSourceTest {
    @Test fun offlineModeRejectsSourceRequestsBeforeAnyNetworkCall() {
        val source = YouTubeMusic(onlineAllowed = { false })
        assertThrows(IOException::class.java) { source.searchSongs("test") }
        assertThrows(IOException::class.java) { source.searchAlbums("test") }
        assertThrows(IOException::class.java) { source.searchPlaylists("test") }
        assertThrows(IOException::class.java) { source.albumTracks(OnlineAlbum("https://example.com", "test", null, null)) }
        assertThrows(IOException::class.java) { source.related("test") }
        assertThrows(IOException::class.java) { source.resolve("test") }
    }
}
