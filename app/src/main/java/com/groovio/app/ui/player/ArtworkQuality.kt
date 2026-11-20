package com.groovio.app.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.groovio.app.GroovioApplication

/** Changes the requested image URL, never upscales the already-loaded bitmap. */
object ArtworkQuality {
    private val googleSize = Regex("=w\\d+-h\\d+")
    private val ytSize = Regex("/(default|mqdefault|hqdefault|sddefault|hq720|maxresdefault)\\.(jpg|webp)(?=\\?|$)", RegexOption.IGNORE_CASE)

    /** Returning null means the source does not advertise a larger image variant. */
    fun highResolutionUrl(url: String): String? {
        val source = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (source.scheme != "https") return null
        val host = source.host?.lowercase() ?: return null
        if (host == "yt3.googleusercontent.com" || host.endsWith(".googleusercontent.com")) {
            val result = url.replaceFirst(googleSize, "=w1080-h1080")
            return result.takeIf { it != url }
        }
        if (host == "i.ytimg.com" || host == "img.youtube.com") {
            // YouTube's 1280px cover, if the uploader supplied it. Coil falls back on 404.
            val match = ytSize.find(url) ?: return null
            val result = url.replaceRange(match.range, "/maxresdefault.${match.groupValues[2].lowercase()}")
            return result.takeIf { it != url }
        }
        return null
    }
}

/**
 * Network-aware cover art. Keep original URLs on mobile data unless explicitly opted in.
 * A missing high-res image retries the original; no fabricated sharpening/AI upscaling.
 */
@Composable
fun GroovioArtwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    highDetail: Boolean = true,
) {
    val app = LocalContext.current.applicationContext as GroovioApplication
    val wifi by app.connectivity.wifiConnected.collectAsStateWithLifecycle()
    val highMobile by app.settings.highResolutionMobileArtwork.collectAsStateWithLifecycle()
    val upgraded = remember(model, wifi, highMobile, highDetail) {
        if (highDetail && (wifi || highMobile)) {
            (model as? String)?.let(ArtworkQuality::highResolutionUrl)
        } else null
    }
    var useOriginal by remember(model, upgraded) { mutableStateOf(false) }
    AsyncImage(
        model = if (useOriginal || upgraded == null) model else upgraded,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = { if (!useOriginal && upgraded != null) useOriginal = true },
    )
}
