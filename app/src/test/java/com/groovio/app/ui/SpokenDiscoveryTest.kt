package com.groovio.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.groovio.app.online.OnlineTrack
import com.groovio.app.spoken.SpokenDiscovery
import com.groovio.app.ui.theme.GroovioTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpokenDiscoveryTest {
    @get:Rule val compose = createComposeRule()
    private val book = OnlineTrack("book", "Story One", "Reader", 7200000, null)
    @Test fun `book feed uses listening cards with functional save and play and no large search form`() {
        var played = false; var saved = false
        compose.setContent { GroovioTheme {
            SpokenDiscovery("Audiobooks", listOf(book), emptyList(), emptyList(), emptyList(), emptySet(), false, false, true, null, "", {},
                onPlay = { played = it.id == "book" }, onSave = { saved = it.id == "book" }, onRemove = {}, onDownload = {}, onOpen = {}, onOpenSaved = {})
        } }
        compose.onNodeWithText("Audiobooks for you").assertIsDisplayed()
        compose.onNodeWithText("Public uploads available on YouTube.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Search books and authors").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save Story One").performClick()
        compose.onNodeWithText("Listen").performClick()
        assertTrue(saved); assertTrue(played)
        compose.onNodeWithContentDescription("Search Audiobooks").performClick()
        compose.onNodeWithText("Search books and authors").assertIsDisplayed()
    }
    @Test fun `following is a working filter rather than an empty decorative control`() {
        compose.setContent { GroovioTheme {
            SpokenDiscovery("Podcasts", listOf(book), emptyList(), emptyList(), emptyList(), emptySet(), false, false, true, null, "", {},
                onPlay = {}, onSave = {}, onRemove = {}, onDownload = {}, onOpen = {}, onOpenSaved = {})
        } }
        compose.onNodeWithText("Following").performClick()
        compose.onNodeWithText("Your shows").assertIsDisplayed()
        compose.onNodeWithText("Follow a show to keep its episodes here.").assertIsDisplayed()
        compose.onNodeWithText("Story One").assertDoesNotExist()
    }
    @Test fun `saved books stay accessible without depending on discovery results`() {
        compose.setContent { GroovioTheme {
            SpokenDiscovery("Audiobooks", emptyList(), emptyList(), emptyList(),
                listOf(com.groovio.app.spoken.SpokenItem(book, "Audiobooks")), emptySet(), false, false, true, null, "", {},
                onPlay = {}, onSave = {}, onRemove = {}, onDownload = {}, onOpen = {}, onOpenSaved = {})
        } }
        compose.onNodeWithText("Saved").performClick()
        compose.onNodeWithText("Your books").assertIsDisplayed()
        compose.onNodeWithText("Story One").assertIsDisplayed()
    }
}
