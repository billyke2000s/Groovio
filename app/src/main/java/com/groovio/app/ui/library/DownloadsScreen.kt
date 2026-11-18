package com.groovio.app.ui.library

import androidx.compose.runtime.Composable
import com.groovio.app.data.TrackEntity

/** Compatibility entry point. Downloads and Local Files are now one Your music UI. */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onMore: (TrackEntity) -> Unit,
) {
    LocalFilesScreen(
        currentMediaId = null,
        onPlay = onPlay,
        onMore = onMore,
        onPlayLegacy = {},
        onBack = onBack,
        onSettings = {},
    )
}

fun offlineMessage(count: Int): String = if (count > 0)
    "Offline · $count saved songs · Open Your music"
else "Offline · Open Your music"
