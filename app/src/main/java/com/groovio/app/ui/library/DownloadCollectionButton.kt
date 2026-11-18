package com.groovio.app.ui.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity
import com.groovio.app.ui.theme.GroovioColors

/**
 * An actual download action with download/progress/saved states. It intentionally
 * keeps Download & play's original action: enqueue then start playback.
 */
@Composable
fun DownloadCollectionButton(tracks: List<TrackEntity>, onPlay: () -> Unit) {
    val app = LocalContext.current.applicationContext as GroovioApplication
    val enabled by app.settings.downloadsEnabled.collectAsStateWithLifecycle()
    val online by app.connectivity.online.collectAsStateWithLifecycle()
    val offlineOnly by app.settings.offlineOnly.collectAsStateWithLifecycle()
    val ids = tracks.filter { it.mediaId.startsWith("yt:") }.map { it.mediaId }.distinct()
    if (ids.isEmpty() || !enabled) return

    // Do not initialise WorkManager just to display a playlist while
    // downloads are disabled, including in offline/preview/test contexts.
    val saved by app.downloads.tracks.collectAsStateWithLifecycle()
    val jobs by app.downloads.jobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val stored = saved.map { it.mediaId }.toSet()
    val allSaved = ids.all { it in stored }
    val active = jobs.filter { job ->
        !job.state.isFinished && ids.any { it in job.tags }
    }
    val downloading = active.isNotEmpty()
    val state = when {
        downloading -> "downloading"
        allSaved -> "saved"
        else -> "ready"
    }
    val determinate = active.any { it.progress.getInt("percent", 0) > 0 }
    val progress = if (active.isEmpty()) 0f else
        active.map { it.progress.getInt("percent", 0).coerceIn(0, 100) }.average().toFloat() / 100f
    val smoothProgress by animateFloatAsState(progress, tween(240, easing = FastOutSlowInEasing),
        label = "Download progress")
    val label = when (state) {
        "downloading" -> "Downloading playlist"
        "saved" -> "Downloaded — play"
        else -> "Download and play"
    }
    OutlinedIconButton(
        onClick = {
            when {
                allSaved -> onPlay()
                online && !offlineOnly && !downloading -> {
                    app.downloads.enqueue(tracks)
                    onPlay()
                }
            }
        },
        enabled = state == "saved" || (!downloading && online && !offlineOnly),
        modifier = Modifier.size(48.dp),
        border = BorderStroke(1.dp, GroovioColors.TextMuted),
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = GroovioColors.Accent),
    ) {
        Crossfade(targetState = state, animationSpec = tween(200), label = "Download state") { shown ->
            when (shown) {
                "downloading" -> {
                    if (determinate) {
                        CircularProgressIndicator(progress = { smoothProgress },
                            modifier = Modifier.size(27.dp), color = GroovioColors.Accent,
                            strokeWidth = 2.5.dp)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(27.dp),
                            color = GroovioColors.Accent, strokeWidth = 2.5.dp)
                    }
                }
                "saved" -> Icon(Icons.Default.Check, contentDescription = label,
                    modifier = Modifier.size(24.dp))
                else -> Icon(Icons.Default.Download, contentDescription = label,
                    modifier = Modifier.size(24.dp))
            }
        }
    }
}
