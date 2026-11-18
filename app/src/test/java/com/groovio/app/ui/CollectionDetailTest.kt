package com.groovio.app.ui

import androidx.compose.ui.test.*
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import androidx.compose.ui.test.junit4.createComposeRule
import com.groovio.app.GroovioApplication
import com.groovio.app.data.RecentCollection
import com.groovio.app.data.TrackEntity
import com.groovio.app.ui.browse.CollectionDetailScreen
import com.groovio.app.ui.theme.GroovioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = GroovioApplication::class)
class CollectionDetailTest {
    @get:Rule val compose = createComposeRule()

    @Before fun initialiseDownloads() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext<GroovioApplication>()
        )
    }
    private val songs = listOf(
        TrackEntity("yt:one", "Que Sera, Sera", "Doris Day", null),
        TrackEntity("yt:two", "That's Amore", "Dean Martin", null),
    )
    private val collection = RecentCollection("playlist:test", "Oldies playlist", "Playlist", null, songs)

    @Test fun openingCollectionShowsTracksWithoutPlayingThem() {
        var playCount = 0
        compose.setContent { GroovioTheme {
            CollectionDetailScreen(collection, songs, null,
                onBack = {}, onPlay = { _, _ -> playCount++ }, onMore = {})
        } }
        compose.onNodeWithText("Oldies playlist").assertIsDisplayed()
        assertEquals(0, playCount)
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Play playlist"))
        compose.onNodeWithContentDescription("Play playlist").performClick()
        assertEquals(1, playCount)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Que Sera, Sera"))
        compose.onNodeWithText("Que Sera, Sera").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("That's Amore"))
        compose.onNodeWithText("That's Amore").assertExists()
    }

    @Test fun tappingSongStartsAtItsIndexAndBackClosesPage() {
        var playedIndex = -1
        var closed = false
        compose.setContent { GroovioTheme {
            CollectionDetailScreen(collection, songs, null,
                onBack = { closed = true },
                onPlay = { _, i -> playedIndex = i }, onMore = {})
        } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("That's Amore"))
        compose.onNodeWithText("That's Amore").performClick()
        assertEquals(1, playedIndex)
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Back"))
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(closed)
    }
}
