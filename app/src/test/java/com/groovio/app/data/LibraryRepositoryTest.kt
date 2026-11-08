package com.groovio.app.data

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room database (in memory) on the JVM via Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRepositoryTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepository
    private var now = 1_000L

    private fun track(id: String, title: String = id) = TrackEntity("yt:$id", title, "Artist", null)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LibraryRepository(db.dao()) { now }
    }

    @After fun tearDown() = db.close()

    @Test fun `liking and unliking a song`() = runBlocking {
        val a = track("a")
        assertFalse(repo.isFavourite(a.mediaId).first())
        repo.setFavourite(a, true)
        repo.setFavourite(a, true) // twice is harmless
        assertTrue(repo.isFavourite(a.mediaId).first())
        assertEquals(listOf(a), repo.favourites.first())
        repo.setFavourite(a, false)
        assertTrue(repo.favourites.first().isEmpty())
    }

    @Test fun `liked songs are newest first`() = runBlocking {
        repo.setFavourite(track("a"), true); now += 1
        repo.setFavourite(track("b"), true)
        assertEquals(listOf("yt:b", "yt:a"), repo.favourites.first().map { it.mediaId })
    }

    @Test fun `recently played lists each song once, latest play first`() = runBlocking {
        repo.recordPlay(track("a")); now += 1
        repo.recordPlay(track("b")); now += 1
        repo.recordPlay(track("a"))
        assertEquals(listOf("yt:a", "yt:b"), repo.recent().first().map { it.mediaId })
    }

    @Test fun `old history is trimmed`() = runBlocking {
        repo.recordPlay(track("old"))
        now += 200L * 24 * 60 * 60 * 1000
        repo.recordPlay(track("new"))
        repo.trimHistory(keepDays = 180)
        assertEquals(listOf("yt:new"), repo.recent().first().map { it.mediaId })
    }

    @Test fun `playlist keeps order, allows duplicates, and removes by position`() = runBlocking {
        val id = repo.createPlaylist("  Road trip ")
        repo.addToPlaylist(id, track("a"))
        repo.addToPlaylist(id, track("b"))
        repo.addToPlaylist(id, track("a"))
        assertEquals(listOf("yt:a", "yt:b", "yt:a"), repo.playlistItems(id).first().map { it.track.mediaId })
        assertEquals("Road trip", repo.playlistName(id).first())

        repo.removeFromPlaylist(id, 1)
        assertEquals(listOf("yt:a", "yt:a"), repo.playlistItems(id).first().map { it.track.mediaId })
        // Appending after a removal goes to the end.
        repo.addToPlaylist(id, track("c"))
        assertEquals(listOf("yt:a", "yt:a", "yt:c"), repo.playlistItems(id).first().map { it.track.mediaId })
        assertEquals(3, repo.playlists.first().single().trackCount)
    }

    @Test fun `deleting a playlist removes its entries but keeps likes`() = runBlocking {
        val id = repo.createPlaylist("Mix")
        repo.addToPlaylist(id, track("a"))
        repo.setFavourite(track("a"), true)
        repo.deletePlaylist(id)
        assertTrue(repo.playlists.first().isEmpty())
        assertTrue(repo.playlistItems(id).first().isEmpty())
        assertEquals(1, repo.favourites.first().size)
    }

    @Test fun `queue is saved and restored with position and modes`() = runBlocking {
        val items = listOf(item("yt:a", "A"), item("yt:b", "B"), item("yt:a", "A"))
        repo.saveQueue(items, index = 1, positionMs = 42_000, shuffle = true, repeatMode = 2)
        val saved = repo.loadQueue()!!
        assertEquals(listOf("yt:a", "yt:b", "yt:a"), saved.items.map { it.mediaId })
        assertEquals("groovio://youtube/b", saved.items[1].localConfiguration?.uri.toString())
        assertEquals(1, saved.index)
        assertEquals(42_000L, saved.positionMs)
        assertTrue(saved.shuffle)
        assertEquals(2, saved.repeatMode)

        // Saving a shorter queue replaces the old one.
        repo.saveQueue(listOf(item("yt:c", "C")), 0, 0, false, 0)
        assertEquals(listOf("yt:c"), repo.loadQueue()!!.items.map { it.mediaId })
    }

    @Test fun `nothing to restore on first run`() = runBlocking {
        assertNull(repo.loadQueue())
    }

    @Test fun `local files keep their content uri as identity`() {
        val uri = "content://com.android.providers.media.documents/document/audio%3A42"
        assertEquals(uri, TrackMapping.uriFor(uri))
        assertEquals("groovio://youtube/xyz", TrackMapping.uriFor("yt:xyz"))
    }

    private fun item(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setUri(TrackMapping.uriFor(id))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()
}
