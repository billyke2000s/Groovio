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
}
