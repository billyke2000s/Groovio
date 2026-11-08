package com.groovio.app.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.groovio.app.ui.theme.GroovioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A dark tint taken from the artwork (like Spotify's player background), animated
 * between songs. Falls back to the surface colour when there is no artwork.
 */
@Composable
fun rememberArtworkTint(bitmap: ImageBitmap?, url: String?): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(GroovioColors.Surface) }
    LaunchedEffect(bitmap, url) {
        val source: Bitmap? = when {
            bitmap != null -> bitmap.asAndroidBitmap()
            url != null -> {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(128)
                    .allowHardware(false) // Palette needs to read pixels.
                    .build()
                (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
            }
            else -> null
        }
        target = source?.let { bmp -> withContext(Dispatchers.Default) { tintFrom(bmp) } }
            ?: GroovioColors.Surface
    }
    val animated by animateColorAsState(target, animationSpec = tween(600), label = "artworkTint")
    return animated
}

private fun tintFrom(bitmap: Bitmap): Color? = try {
    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
    val swatch = palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.dominantSwatch
    // Darken so white text stays readable on top.
    swatch?.let { lerp(Color(it.rgb), Color.Black, 0.45f) }
} catch (_: Exception) {
    null
}
