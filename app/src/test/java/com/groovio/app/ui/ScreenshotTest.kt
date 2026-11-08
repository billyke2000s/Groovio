package com.groovio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.media3.common.Player
import com.github.takahirom.roborazzi.captureRoboImage
import com.groovio.app.data.PlaylistSummary
import com.groovio.app.data.TrackEntity
import com.groovio.app.playback.PlayerUiState
import com.groovio.app.playback.QueueEntry
import com.groovio.app.ui.home.HomeScreen
import com.groovio.app.ui.player.MiniPlayer
import com.groovio.app.ui.player.PlayerScreen
import com.groovio.app.ui.player.QueueScreen
import com.groovio.app.ui.theme.GroovioColors
import com.groovio.app.ui.theme.GroovioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders key screens to PNGs (only when GROOVIO_SCREENSHOTS=1) so the UI can be reviewed
 * without a device. Images use sample data and no network artwork.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val playing = PlayerUiState(
        connected = true, hasItem = true,
        title = "Get Lucky (feat. Pharrell Williams and Nile Rodgers)",
        artist = "Daft Punk", isPlaying = true, durationMs = 369_000, hasNext = true,
        shuffle = true, repeatMode = Player.REPEAT_MODE_ALL, audioLabel = "OPUS · 160K · 48kHz",
        queue = listOf(
            QueueEntry(0, "Give Life Back to Music", "Daft Punk"),
            QueueEntry(1, "Get Lucky", "Daft Punk"),
            QueueEntry(2, "Instant Crush", "Daft Punk"),
        ),
        currentIndex = 1, currentMediaId = "yt:b",
    )

    private val recent = listOf("Get Lucky", "Instant Crush", "Lose Yourself to Dance", "Doin' it Right")
        .mapIndexed { i, t -> TrackEntity("yt:$i", t, "Daft Punk", null) }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            GroovioTheme { Box(Modifier.fillMaxSize().background(GroovioColors.Background)) { content() } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun home() = shot("home") {
        HomeScreen(
            recent = recent, likedCount = 12,
            playlists = listOf(PlaylistSummary(1, "Road trip", 24), PlaylistSummary(2, "Gym", 31)),
            currentMediaId = "yt:0", onPlayRecent = { _, _ -> }, onOpenLiked = {}, onOpenPlaylist = {},
            onSearch = {}, onOpenFiles = {},
        )
    }

    @Test fun player() = shot("player") {
        PlayerScreen(
            state = playing, positionMs = 95_000, onCollapse = {}, onOpenQueue = {}, onTogglePlay = {},
            onSeek = {}, onNext = {}, onPrevious = {}, onToggleShuffle = {}, onCycleRepeat = {},
            isFavourite = true, onToggleFavourite = {}, playlists = emptyList(),
            onAddToPlaylist = {}, onAddToNewPlaylist = {}, onOpenArtist = {},
        )
    }

    @Test fun miniPlayer() = shot("mini_player") {
        MiniPlayer(state = playing, positionMs = 95_000, onOpen = {}, onTogglePlay = {}, onNext = {}, onPrevious = {})
    }

    @Test fun queue() = shot("queue") {
        QueueScreen(state = playing, onBack = {}, onPlayAt = {}, onRemoveAt = {})
    }
}
