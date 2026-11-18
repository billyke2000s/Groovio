package com.groovio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.github.takahirom.roborazzi.captureRoboImage
import com.groovio.app.GroovioApplication
import com.groovio.app.spoken.SpokenDiscovery
import com.groovio.app.ui.theme.GroovioColors
import com.groovio.app.ui.theme.GroovioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
class SpokenLiveScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(kind: String, query: String, filename: String) {
        assumeTrue(System.getenv("GROOVIO_SCREENSHOTS") == "1")
        val app = ApplicationProvider.getApplicationContext<GroovioApplication>()
        val tracks = app.youtube.searchSpoken(query).filter { it.thumbnailUrl != null }.take(3)
        check(tracks.isNotEmpty()) { "No live catalogue entries; cannot capture real content" }
        runBlocking(Dispatchers.IO) {
            tracks.forEach { track ->
                val result = app.imageLoader.execute(ImageRequest.Builder(app).data(track.thumbnailUrl).allowHardware(false).size(400).build())
                check(result is SuccessResult) { "Artwork did not load; do not use blank screenshots" }
            }
        }
        compose.setContent { GroovioTheme { Box(Modifier.fillMaxSize().background(GroovioColors.Background)) {
            SpokenDiscovery(kind, tracks, emptyList(), emptyList(), emptyList(), emptySet(), false, false, true, null, "", {}, {}, {}, {}, {}, {}, {})
        } } }
        repeat(5) { compose.waitForIdle(); Thread.sleep(200) }
        compose.onRoot().captureRoboImage("build/screenshots/$filename.png")
    }
    @Test fun podcastsLive() = capture("Podcasts", "podcast full episode", "spoken_podcasts_live")
    @Test fun audiobooksLive() = capture("Audiobooks", "public domain audiobook full book", "spoken_audiobooks_live")
}
