package com.groovio.app.offline

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.groovio.app.data.TrackEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class OfflineStoreTest {
    @Test fun partialTransfersAreInvisibleAndCompletedFilesSurviveRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "downloads").deleteRecursively()
        val store = OfflineStore(context)
        val track = TrackEntity("yt:offline123", "Offline song", "Artist", null)
        val partial = File(store.file(track.mediaId).path + ".part").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertNull(store.localFile(track.mediaId))
        assertTrue(store.tracks.value.isEmpty())
        store.complete(track, partial) { false }
        assertEquals(track, store.tracks.value.single())
        val recreated = OfflineStore(context)
        assertEquals(track, recreated.tracks.value.single())
        assertArrayEquals(byteArrayOf(1, 2, 3), recreated.localFile(track.mediaId)!!.readBytes())
    }
    @Test fun cancelledTransferIsNeverPublished() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "downloads").deleteRecursively()
        val store = OfflineStore(context)
        val track = TrackEntity("yt:cancelled123", "Cancelled", null, null)
        val partial = File(store.file(track.mediaId).path + ".part").apply { writeText("partial") }
        assertThrows(java.io.IOException::class.java) { store.complete(track, partial) { true } }
        assertNull(store.localFile(track.mediaId))
        assertTrue(store.tracks.value.isEmpty())
    }
    @Test fun truncatedSavedFilesAreNotAdvertisedAsDownloaded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "downloads").deleteRecursively()
        val store = OfflineStore(context)
        val track = TrackEntity("yt:truncated", "Truncated", null, null)
        val partial = File(store.file(track.mediaId).path + ".part").apply { writeBytes(ByteArray(20)) }
        store.complete(track, partial) { false }
        store.file(track.mediaId).writeBytes(ByteArray(5))
        assertNull(store.localFile(track.mediaId))
        assertTrue(OfflineStore(context).tracks.value.isEmpty())
    }
}
