package com.groovio.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(onBack: () -> Unit, onPlay: (List<TrackEntity>, Int) -> Unit) {
    BackHandler(onBack = onBack)
    val app = LocalContext.current.applicationContext as GroovioApplication
    val tracks by app.downloads.tracks.collectAsStateWithLifecycle()
    val jobs by app.downloads.jobs.collectAsStateWithLifecycle(emptyList())
    val online by app.connectivity.online.collectAsStateWithLifecycle()
    val offlineOnly by app.settings.offlineOnly.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<TrackEntity?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val active = jobs.filter { !it.state.isFinished }
    val failed = jobs.filter { it.state == WorkInfo.State.FAILED }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Back") }
            TextButton(onClick = { onPlay(tracks, 0) }, enabled = tracks.isNotEmpty()) { Text("Play all") }
        }
        Text("Downloads", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Text(if (!online || offlineOnly) offlineMessage(tracks.size) else "${tracks.size} songs · ${app.downloads.bytes() / (1024 * 1024)} MB saved",
            modifier = Modifier.padding(20.dp))
        if (active.isNotEmpty()) {
            Text("${active.size} downloads pending or running. Wi-Fi-only downloads wait for an unmetered connection.", modifier = Modifier.padding(horizontal = 20.dp))
            TextButton(onClick = app.downloads::cancelAll) { Text("Cancel pending downloads") }
        }
        if (failed.isNotEmpty()) Text("${failed.size} downloads couldn't finish. Use Download & play again to retry.", modifier = Modifier.padding(20.dp))
        note?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp)) }
        if (tracks.isEmpty()) Text("Save a song or playlist using Download & play. It'll be here whenever you need it.", modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(active, key = { it.id }) { job ->
                val title = job.tags.firstOrNull { it.startsWith("title:") }?.removePrefix("title:")
                Text("${title ?: "Song"} · ${if (job.state == WorkInfo.State.RUNNING) "${job.progress.getInt("percent", 0)}%" else "Waiting"}", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            items(tracks, key = { it.mediaId }) { track ->
                Row(Modifier.fillMaxWidth().clickable { onPlay(tracks, tracks.indexOf(track)) }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) { Text(track.title); track.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    TextButton(onClick = { deleting = track }) { Text("Remove") }
                }
            }
        }
    }
    deleting?.let { track ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove download?") },
            text = { Text("Remove ${track.title} from this phone? Your likes and playlists stay.") },
            confirmButton = { TextButton(onClick = {
                deleting = null
                scope.launch { runCatching { app.downloads.remove(track) }.onFailure { note = "Couldn't remove this download." } }
            }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

fun offlineMessage(count: Int): String = if (count > 0)
    "You're offline, but your music is still with you. $count saved songs, a little space to breathe — keep your rhythm going."
else "A quiet moment can be a fresh start. Your local audio files are still yours to play; save some songs when you're connected."
