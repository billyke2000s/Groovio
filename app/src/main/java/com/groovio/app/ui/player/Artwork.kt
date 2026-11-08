package com.groovio.app.ui.player

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.groovio.app.ui.theme.GroovioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes embedded artwork off the main thread, downsampled to about [targetPx]. */
@Composable
fun rememberArtwork(data: ByteArray?, targetPx: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, data, targetPx) {
        value = data?.let { bytes ->
            withContext(Dispatchers.Default) { decodeSampled(bytes, targetPx) }
        }
    }
    return bitmap
}

private fun decodeSampled(bytes: ByteArray, targetPx: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
} catch (_: Throwable) {
    // Corrupt or huge artwork must never crash playback UI.
    null
}

/** Square artwork, or a neutral fallback when the track has none. */
@Composable
fun ArtworkImage(bitmap: ImageBitmap?, iconSize: Dp, modifier: Modifier = Modifier) {
    Box(modifier.background(GroovioColors.ArtworkFallback), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = GroovioColors.Accent,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
