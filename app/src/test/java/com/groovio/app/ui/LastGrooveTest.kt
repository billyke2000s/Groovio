package com.groovio.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity
import com.groovio.app.ui.search.SearchScreen
import com.groovio.app.ui.search.SearchViewModel
import com.groovio.app.ui.theme.GroovioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastGrooveTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `empty full screen search offers replay and a close action`() {
        val app = ApplicationProvider.getApplicationContext<GroovioApplication>()
        val tracks = listOf(TrackEntity("yt:one", "A song inside", "Artist", null))
        val recent = listOf(com.groovio.app.data.RecentCollection("playlist:1", "A recent playlist", "Playlist", null, tracks, playlistId = 1))
        var replayed = false
        var closed = false
        val vm = SearchViewModel(app)
        compose.setContent { GroovioTheme {
            SearchScreen(vm = vm, currentMediaId = null, onPlay = { _, _ -> },
                spotifyImport = null, onPlaySpotify = {}, onOpenAlbum = {}, onMore = {},
                recentCollections = recent, onReplayCollection = { collection -> replayed = collection.id == "playlist:1" && collection.tracks == tracks },
                onBack = { closed = true })
        } }
        compose.onNodeWithText("Last Groove").assertIsDisplayed()
        compose.onNodeWithText("A recent playlist").performClick()
        compose.onNodeWithText("A song inside").assertDoesNotExist()
        assertTrue(replayed)
        compose.onNodeWithContentDescription("Close search").performClick()
        assertTrue(closed)
    }
}
