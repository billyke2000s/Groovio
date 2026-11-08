package com.groovio.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.MoreExecutors
import androidx.media3.common.util.UnstableApi
import com.groovio.app.playback.GroovioPlaybackService

private val Green = Color(0xFF1ED760)
private val Background = Color(0xFF101318)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GroovioScreen() }
    }
}

@Composable
private fun GroovioScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf("Nothing playing") }
    var playing by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf("Select an audio file") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Local track"
            title = name
            format = "Format available after playback"
            controller?.apply {
                setMediaItem(MediaItem.Builder().setUri(uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build()).build())
                prepare()
                play()
            }
        }
    }
    DisposableEffect(context) {
        val token = SessionToken(context, android.content.ComponentName(context, GroovioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                playing = c.isPlaying
            } catch (_: Exception) {
                format = "Player unavailable"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    DisposableEffect(controller) {
        val c = controller
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                title = mediaMetadata.title?.toString() ?: title
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val audio = tracks.groups.firstOrNull {
                    it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSelected
                }?.getTrackFormat(0)
                format = if (audio != null) {
                    listOfNotNull(
                        audio.sampleMimeType?.substringAfterLast('/ ')?.uppercase()?.takeIf { it.isNotBlank() },
                        audio.bitrate.takeIf { it > 0 }?.let { "${it / 1000}K" },
                        audio.sampleRate.takeIf { it > 0 }?.let { "${it / 1000.0}kHz" }
                    ).joinToString(" · ").ifBlank { "Audio format unknown" }
                } else "Audio format unknown"
            }
        }
        c?.addListener(listener)
        onDispose { c?.removeListener(listener) }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Background)) {
        Column(
            Modifier.fillMaxSize().background(Background).padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("GROOVIO", color = Green, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("LOCAL PLAYER", color = Color.LightGray, fontSize = 12.sp)
            }
            Box(Modifier.fillMaxWidth().aspectRatio(1f)
                .background(Color(0xFF23382D), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = "Album artwork placeholder",
                    tint = Green, modifier = Modifier.size(112.dp))
            }
            Column {
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(format, color = Green, fontSize = 13.sp)
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                        Icon(Icons.Default.SkipPrevious, "Previous track")
                    }
                    FilledIconButton(
                        onClick = { if (playing) controller?.pause() else controller?.play() },
                        modifier = Modifier.size(72.dp),
                        enabled = controller?.mediaItemCount != 0 && controller != null
                    ) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playing) "Pause" else "Play", modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                        Icon(Icons.Default.SkipNext, "Next track")
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { picker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open audio file")
                }
            }
        }
    }
}
