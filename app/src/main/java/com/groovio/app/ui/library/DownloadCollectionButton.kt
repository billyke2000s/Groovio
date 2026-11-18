package com.groovio.app.ui.library

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity

@Composable
fun DownloadCollectionButton(tracks: List<TrackEntity>, onPlay: () -> Unit) {
    val app = LocalContext.current.applicationContext as GroovioApplication
    val enabled by app.settings.downloadsEnabled.collectAsStateWithLifecycle()
    val online by app.connectivity.online.collectAsStateWithLifecycle()
    val offlineOnly by app.settings.offlineOnly.collectAsStateWithLifecycle()
    if (enabled && online && !offlineOnly && tracks.any { it.mediaId.startsWith("yt:") }) {
        OutlinedButton(onClick = { app.downloads.enqueue(tracks); onPlay() }) { Text("Download & play") }
    }
}
