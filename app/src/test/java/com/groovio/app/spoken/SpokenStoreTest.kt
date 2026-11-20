package com.groovio.app.spoken

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.groovio.app.online.OnlineTrack
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpokenStoreTest {
    @Test fun `saved book and progress survive recreation and backup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spoken_library", Context.MODE_PRIVATE).edit().clear().commit()
        val store = SpokenStore(context)
        store.save(OnlineTrack("abc", "Book", "Author", 10000, "https://art"), "Audiobooks")
        store.progress("yt:abc", 4500)
        val recreated = SpokenStore(context)
        assertEquals(4500L, recreated.position("yt:abc"))
        recreated.restore(store.export())
        assertEquals(1, recreated.saved.value.size)
        assertEquals("Audiobooks", recreated.saved.value.single().kind)
    }
    @Test fun `invalid imports never create arbitrary URI tracks`() {
        assertTrue(SpokenStore.decode("""[{"id":"file:///private","title":"Bad","kind":"Audiobooks"}]""").isEmpty())
    }
    @Test fun `registering a collection in one batch preserves progress and deduplicates episodes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spoken_library", Context.MODE_PRIVATE).edit().clear().commit()
        val store = SpokenStore(context)
        val first = OnlineTrack("one", "First", "Show", 10000, null)
        val second = OnlineTrack("two", "Second", "Show", 20000, null)
        store.save(first, "Podcasts"); store.progress("yt:one", 5000)
        store.saveAll(listOf(first, second, second), "Podcasts")
        assertEquals(listOf("one", "two"), store.saved.value.map { it.track.id })
        assertEquals(5000L, SpokenStore(context).position("yt:one"))
    }

    @Test fun `listening keeps progress without saving a bookmark or downloading`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spoken_library", Context.MODE_PRIVATE).edit().clear().commit()
        val store = SpokenStore(context)
        val track = OnlineTrack("listened", "A chapter", "Narrator", 120_000, null)
        store.remember(track, "Audiobooks")
        assertFalse(store.saved.value.single().inLibrary)
        store.progress("yt:listened", 45_000)
        assertEquals(45_000L, SpokenStore(context).position("yt:listened"))

        store.save(track, "Audiobooks")
        assertTrue(store.saved.value.single().inLibrary)
        store.remove("listened")
        assertFalse(store.saved.value.single().inLibrary)
        assertEquals(45_000L, store.position("listened"))
    }
    @Test fun `finishing a spoken item clears resume position`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spoken_library", Context.MODE_PRIVATE).edit().clear().commit()
        val store = SpokenStore(context)
        store.remember(OnlineTrack("ending", "Ending", null, 120_000, null), "Podcasts")
        store.progress("yt:ending", 119_500)
        assertEquals(0L, store.position("ending"))
    }

}
