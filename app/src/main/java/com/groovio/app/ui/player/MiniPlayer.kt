package com.groovio.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovio.app.playback.PlayerUiState
import com.groovio.app.ui.theme.GroovioColors

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    positionMs: Long,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = rememberArtwork(state.artworkData, targetPx = 128)
    val tint = rememberArtworkTint(artwork, state.artworkUri)
    val duration = state.durationMs ?: 0L

    // Swipe left for next, right for previous.
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val canNext by rememberUpdatedState(state.hasNext)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }

    Surface(
        color = tint,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragX < -threshold && canNext -> next()
                            dragX > threshold -> previous()
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                ) { change, amount ->
                    change.consume()
                    dragX += amount
                }
            }
            .clickable(onClickLabel = "Open player", onClick = onOpen),
    ) {
        Column {
            Row(
                Modifier
                    .offset { IntOffset((dragX * 0.5f).roundToInt(), 0) }
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    bitmap = artwork,
                    url = state.artworkUri,
                    iconSize = 22.dp,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        state.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.artist ?: "Unknown artist", fontSize = 12.sp,
                        color = GroovioColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(Icons.Default.SkipNext, "Next")
                }
            }
            LinearProgressIndicator(
                progress = { if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = GroovioColors.Text,
                trackColor = GroovioColors.Surface,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}
