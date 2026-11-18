package com.groovio.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
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
        val recent = listOf(TrackEntity("yt:one", "A recent groove", "Artist", null))
        var replayed = false
        var closed = false
        val vm = SearchViewModel(app)
        compose.setContent { GroovioTheme {
            SearchScreen(vm = vm, currentMediaId = null, onPlay = { _, _ -> },
                spotifyImport = null, onPlaySpotify = {}, onOpenAlbum = {}, onMore = {},
                recent = recent, onPlayRecent = { tracks, index -> replayed = tracks == recent && index == 0 },
                onBack = { closed = true })
        } }
        compose.onNodeWithText("Last Groove").assertIsDisplayed()
        compose.onNodeWithText("A recent groove").performClick()
        assertTrue(replayed)
        compose.onNodeWithContentDescription("Close search").performClick()
        assertTrue(closed)
    }
}
