package com.groovio.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.groovio.app.data.*
import com.groovio.app.ui.home.HomeScreen
import com.groovio.app.ui.theme.GroovioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectionHomeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `jump back in displays the playlist rather than its last song`() {
        val song = TrackEntity("yt:one", "A song inside", "Artist", null)
        val collection = RecentCollection("playlist:1", "Road trip", "Playlist", null, listOf(song))
        var replayed = false
        compose.setContent { GroovioTheme {
            HomeScreen(recent = listOf(song), likedCount = 0, playlists = emptyList(), currentMediaId = null,
                onPlayRecent = { _, _ -> error("Collection replay must not use song history") }, onOpenLiked = {}, onOpenPlaylist = {},
                onSearch = {}, onOpenFiles = {}, onMore = {}, recentCollections = listOf(collection),
                onReplayCollection = { replayed = it.id == collection.id })
        } }
        compose.onNodeWithText("Jump back in").assertIsDisplayed()
        compose.onAllNodesWithText("Road trip").onFirst().performClick()
        compose.onNodeWithText("A song inside").assertDoesNotExist()
        assertTrue(replayed)
    }

    @Test fun `existing collection history is not treated as first run`() {
        val song = TrackEntity("yt:one", "A song inside", "Artist", null)
        val collection = RecentCollection("playlist:1", "Road trip", "Playlist", null, listOf(song))
        compose.setContent { GroovioTheme {
            HomeScreen(recent = emptyList(), likedCount = 0, playlists = emptyList(), currentMediaId = null,
                onPlayRecent = { _, _ -> }, onOpenLiked = {}, onOpenPlaylist = {},
                onSearch = {}, onOpenFiles = {}, onMore = {}, recentCollections = listOf(collection))
        } }
        compose.onNodeWithText("Jump back in").assertIsDisplayed()
        compose.onNodeWithText("Find any song").assertDoesNotExist()
    }
}
