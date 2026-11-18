package com.groovio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import coil.imageLoader
import com.github.takahirom.roborazzi.captureRoboImage
import com.groovio.app.GroovioApplication
import com.groovio.app.spoken.SpokenDiscovery
import com.groovio.app.ui.theme.GroovioColors
import com.groovio.app.ui.theme.GroovioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Optional live-catalogue screen review, separate from offline deterministic regression tests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class SpokenLiveScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(kind: String, query: String, filename: String) {
        assumeTrue(System.getenv("GROOVIO_SCREENSHOTS") == "1")
        val app = ApplicationProvider.getApplicationContext<GroovioApplication>()
        val tracks = runBlocking { withTimeout(45_000) {
            runInterruptible(Dispatchers.IO) { app.youtube.searchSpoken(query).filter { it.thumbnailUrl != null }.take(3) }
        } }
        check(tracks.isNotEmpty()) { "No live catalogue entries; cannot capture real content" }
        compose.setContent { GroovioTheme { Box(Modifier.fillMaxSize().background(GroovioColors.Background)) {
            SpokenDiscovery(kind, tracks, emptyList(), emptyList(), emptyList(), emptySet(), false, false, true, null, "", {}, {}, {}, {}, {}, {}, {})
        } } }
        // Let Compose/Robolectric pump the main looper while Coil loads. Blocking a
        // prefetch here prevents Coil's main-thread callbacks from ever completing.
        compose.waitUntil(timeoutMillis = 30_000) {
            val keys = app.imageLoader.memoryCache?.keys.orEmpty()
            tracks.all { track -> keys.any { it.key.contains(track.thumbnailUrl!!) } }
        }
        repeat(5) { compose.waitForIdle(); Thread.sleep(200) }
        compose.onRoot().captureRoboImage("build/screenshots/$filename.png")
    }
    @Test fun podcastsLive() = capture("Podcasts", "podcast full episode", "spoken_podcasts_live")
    @Test fun audiobooksLive() = capture("Audiobooks", "public domain audiobook full book", "spoken_audiobooks_live")
}
