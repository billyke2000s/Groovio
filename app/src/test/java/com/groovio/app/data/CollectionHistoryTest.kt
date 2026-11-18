package com.groovio.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionHistoryTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("collection_history", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun collection(id: String, time: Long) = RecentCollection(id, "A playlist", "Playlist", "https://art/cover.jpg",
        listOf(TrackEntity("yt:one", "First", "Artist", "https://art/one.jpg"), TrackEntity("yt:two", "Second", "Artist", null)), playedAt = time)
    @Test fun `collection identity and track order survive recreation without duplicate replays`() {
        val store = CollectionHistory(context)
        store.record(collection("playlist:1", 1)); store.record(collection("playlist:2", 2)); store.record(collection("playlist:1", 3))
        val reopened = CollectionHistory(context)
        assertEquals(listOf("playlist:1", "playlist:2"), reopened.recent.value.map { it.id })
        assertEquals(listOf("yt:one", "yt:two"), reopened.recent.value.first().tracks.map { it.mediaId })
        assertEquals("https://art/cover.jpg", reopened.recent.value.first().artworkUri)
    }
    @Test fun `an old asynchronous write cannot reorder newer listening or undo clear history`() {
        val store = CollectionHistory(context)
        store.record(collection("playlist:1", 20)); store.record(collection("playlist:1", 10))
        assertEquals(20L, store.recent.value.single().playedAt)
        store.clear(); store.record(collection("playlist:1", 30))
        assertTrue(CollectionHistory(context).recent.value.isEmpty())
    }
    @Test fun `backup restores snapshots without binding to unrelated local playlist ids`() {
        val store = CollectionHistory(context)
        store.record(collection("playlist:1", 10).copy(playlistId = 1))
        val restored = CollectionHistory.decode(store.export().toString()).single()
        assertEquals(1L, restored.playlistId)
        context.getSharedPreferences("collection_history", Context.MODE_PRIVATE).edit().clear().commit()
        val destination = CollectionHistory(context)
        destination.restore(store.export()); destination.restore(store.export())
        assertNull(destination.recent.value.single().playlistId)
        assertEquals("restored:playlist:1", destination.recent.value.single().id)
    }
    @Test fun `empty queues never become recently played collections`() {
        val store = CollectionHistory(context)
        store.record(collection("empty", 1).copy(tracks = emptyList()))
        assertTrue(store.recent.value.isEmpty())
    }
}
