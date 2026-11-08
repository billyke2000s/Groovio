package com.groovio.app.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import com.groovio.app.playback.SleepTimer
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
    onOpenArtist: (String) -> Unit,
    sleepTimer: SleepTimer? = null,
    onSetSleepTimer: (Int?) -> Unit = {},
) {
    var choosingSleep by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var addingToPlaylist by rememberSaveable { mutableStateOf(false) }
    val artwork = rememberArtwork(state.artworkData, targetPx = 1024)
    val tint = rememberArtworkTint(artwork, state.artworkUri)

    // Swipe down to close, following the finger like Spotify.
    val collapse by rememberUpdatedState(onCollapse)
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.roundToInt()) }
            .background(Brush.verticalGradient(0f to tint, 0.75f to GroovioColors.Background))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY > threshold) collapse()
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                ) { change, amount ->
                    change.consume()
                    dragY = (dragY + amount).coerceAtLeast(0f)
                }
            }
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, "Close player", modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "NOW PLAYING",
                    color = GroovioColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                sleepTimer?.let { t ->
                    val label = if (t.endOfTrack) "Sleep timer: end of song" else {
                        val left = ((t.endsAtMs ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
                        "Sleep timer: ${(left / 60_000) + 1} min"
                    }
                    Text(label, color = GroovioColors.Accent, fontSize = 11.sp)
                }
            }
            IconButton(onClick = { choosingSleep = true }) {
                Icon(
                    Icons.Default.Timer,
                    if (sleepTimer != null) "Sleep timer on" else "Sleep timer",
                    tint = if (sleepTimer != null) GroovioColors.Accent else GroovioColors.Text,
                )
            }
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
                val artist = state.artist
                Text(
                    artist ?: "Unknown artist",
                    color = GroovioColors.TextMuted,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(enabled = artist != null, onClickLabel = "Open artist") {
                            artist?.let(onOpenArtist)
                        },
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

    if (choosingSleep) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { choosingSleep = false },
            title = { Text("Sleep timer") },
            text = {
                Column {
                    listOf<Pair<String, Int?>>(
                        "Off" to null, "5 minutes" to 5, "15 minutes" to 15, "30 minutes" to 30,
                        "45 minutes" to 45, "1 hour" to 60, "End of song" to 0,
                    ).forEach { (label, minutes) ->
                        Text(
                            label,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSetSleepTimer(minutes); choosingSleep = false }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { choosingSleep = false }) { Text("Cancel") } },
        )
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long?, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs ?: 0L
    val fraction = if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column {
        // Spotify-style: thin line with a small round thumb.
        Slider(
            value = dragging ?: fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            thumb = {
                Box(Modifier.size(if (dragging != null) 16.dp else 12.dp).background(GroovioColors.Text, CircleShape))
            },
            track = { sliderState ->
                val range = sliderState.valueRange
                val f = ((sliderState.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(4.dp).background(GroovioColors.SurfaceHigh, CircleShape)) {
                    Box(
                        Modifier.fillMaxWidth(f).height(4.dp)
                            .background(if (dragging != null) GroovioColors.Accent else GroovioColors.Text, CircleShape)
                    )
                }
            },
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
