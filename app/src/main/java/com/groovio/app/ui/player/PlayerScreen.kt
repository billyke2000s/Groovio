package com.groovio.app.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.groovio.app.data.PlaylistSummary
import com.groovio.app.playback.PlaybackText
import com.groovio.app.ui.library.AddToPlaylistDialog
import com.groovio.app.playback.PlayerUiState
import com.groovio.app.ui.theme.GroovioColors

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    positionMs: Long,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    playlists: List<PlaylistSummary>,
    onAddToPlaylist: (Long) -> Unit,
    onAddToNewPlaylist: (String) -> Unit,
) {
    val context = LocalContext.current
    var addingToPlaylist by rememberSaveable { mutableStateOf(false) }
    val artwork = rememberArtwork(state.artworkData, targetPx = 1024)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, "Close player", modifier = Modifier.size(32.dp))
            }
            Text(
                "NOW PLAYING",
                modifier = Modifier.weight(1f),
                color = GroovioColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            // Keeps the title centred; the top bar has one action only.
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.weight(0.6f))
        ArtworkImage(
            bitmap = artwork,
            url = state.artworkUri,
            iconSize = 120.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.weight(0.6f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TitleWithAudioLabel(state.title, state.audioLabel)
                Text(
                    state.artist ?: "Unknown artist",
                    color = GroovioColors.TextMuted,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onToggleFavourite, enabled = state.hasItem) {
                Icon(
                    if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isFavourite) "Remove from Liked songs" else "Add to Liked songs",
                    tint = if (isFavourite) GroovioColors.Accent else GroovioColors.Text,
                )
            }
        }

        state.error?.let {
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(16.dp))
        SeekBar(positionMs, state.durationMs, onSeek)

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Default.Shuffle,
                    if (state.shuffle) "Shuffle on" else "Shuffle off",
                    tint = if (state.shuffle) GroovioColors.Accent else GroovioColors.Text,
                )
            }
            IconButton(onClick = onPrevious, enabled = state.hasItem) {
                Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(40.dp))
            }
            Box(contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = onTogglePlay,
                    enabled = state.hasItem,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = GroovioColors.Text,
                        contentColor = GroovioColors.Background,
                    ),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(38.dp),
                    )
                }
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        color = GroovioColors.Accent,
                        strokeWidth = 3.dp,
                    )
                }
            }
            IconButton(onClick = onNext, enabled = state.hasNext) {
                Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                val (icon, label) = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne to "Repeat one"
                    Player.REPEAT_MODE_ALL -> Icons.Default.Repeat to "Repeat all"
                    else -> Icons.Default.Repeat to "Repeat off"
                }
                Icon(
                    icon, label,
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) GroovioColors.Text
                    else GroovioColors.Accent,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { openOutputSwitcher(context) }) {
                Icon(Icons.Default.SpeakerGroup, "Choose audio output")
            }
            IconButton(onClick = { addingToPlaylist = true }, enabled = state.hasItem) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist")
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue")
            }
        }
    }

    if (addingToPlaylist) {
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = onAddToPlaylist,
            onCreate = onAddToNewPlaylist,
            onDismiss = { addingToPlaylist = false },
        )
    }
}

/**
 * Title followed inline by the actual audio format, e.g. "Get Lucky · OPUS · 160K · 48kHz".
 * One Text, so a long title and its label scroll together.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleWithAudioLabel(title: String, audioLabel: String?) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GroovioColors.Text)) {
            append(title)
        }
        if (audioLabel != null) {
            withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GroovioColors.Accent)) {
                append("  ·  ")
                append(audioLabel)
            }
        }
    }
    Text(
        text,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee(iterations = Int.MAX_VALUE)
            .semantics {
                contentDescription = if (audioLabel != null) "$title, $audioLabel" else title
            },
    )
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long?, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs ?: 0L
    val fraction = if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column {
        Slider(
            value = dragging ?: fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = GroovioColors.Text,
                activeTrackColor = GroovioColors.Accent,
                inactiveTrackColor = GroovioColors.SurfaceHigh,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = dragging?.let { (it * duration).toLong() } ?: positionMs
            Text(PlaybackText.time(shown), color = GroovioColors.TextMuted, fontSize = 12.sp)
            Text(
                if (durationMs != null) PlaybackText.time(durationMs) else "--:--",
                color = GroovioColors.TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}
