package com.groovio.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LibraryBackupTest {
    @Test fun backupRoundTripMergesWithoutDeletingExistingPlaylists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        try {
            val library = LibraryRepository(db.dao())
            val song = TrackEntity("yt:test123", "Song", "Artist", null)
            library.setFavourite(song, true)
            val id = library.createPlaylist("Road trip")
            library.addToPlaylist(id, song); library.addToPlaylist(id, song)
            val backup = library.exportBackup()
            library.restoreBackup(backup)
            assertEquals(1, library.favourites.first().size)
            assertEquals(2, library.playlists.first().size)
            assertEquals(2, library.playlistItems(library.playlists.first().first().id).first().size)
            assertEquals("Song", LibraryBackup.parse(backup).tracks.single().title)
        } finally { db.close() }
    }
    @Test fun invalidReferencesAreRejectedBeforeRestore() {
        val j = JSONObject("""{"format":"groovio","version":1,"tracks":[],"likes":[{"id":"yt:missing","at":1}],"playlists":[],"entries":[]}""")
        assertThrows(IllegalArgumentException::class.java) { LibraryBackup.parse(j.toString()) }
    }
    @Test fun filePathsCannotBeInjectedAsTrackIds() {
        val text = """{"format":"groovio","version":1,"tracks":[{"id":"file:///data/private","title":"bad"}],"likes":[],"playlists":[],"entries":[]}"""
        assertThrows(IllegalArgumentException::class.java) { LibraryBackup.parse(text) }
    }
    @Test fun settingsSurviveRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = GroovioSettings(context)
        first.setNormalise(true); first.setWifiOnly(false); first.setOfflineOnly(true)
        val second = GroovioSettings(context)
        assertTrue(second.normalise.value); assertFalse(second.wifiOnly.value); assertTrue(second.offlineOnly.value)
        second.restore(first.export())
        assertTrue(second.normalise.value)
    }
}
