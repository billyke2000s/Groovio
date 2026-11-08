package com.groovio.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovio.app.playback.PlayerUiState
import com.groovio.app.playback.QueueEntry
import com.groovio.app.ui.theme.GroovioColors
import kotlin.math.roundToInt

/** Spotify-style queue: what's playing now, then what's next (drag the handle to reorder). */
@Composable
fun QueueScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val current = state.queue.getOrNull(state.currentIndex)
    val upcoming = state.queue.drop(state.currentIndex + 1)
    // Row being dragged (queue index) and how far it has moved.
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }
    val move by rememberUpdatedState(onMove)
    // Read inside long-lived gesture handlers, so keep them current.
    val last by rememberUpdatedState(state.queue.lastIndex)
    val currentIndex by rememberUpdatedState(state.currentIndex)

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Close queue") }
            Text("Queue", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (state.queue.isEmpty()) {
            Text("Nothing queued.", color = GroovioColors.TextMuted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (current != null) {
                item { Header("Now playing") }
                item(key = "now") { QueueRow(current, isCurrent = true, onClick = {}, onRemove = null, handle = null) }
            }
            if (upcoming.isNotEmpty()) item { Header("Next up") }
            items(upcoming, key = { it.index }) { entry ->
                val isDragged = dragging == entry.index
                Column(
                    Modifier
                        .onSizeChanged { rowHeight = it.height.toFloat().coerceAtLeast(1f) }
                        .offset { IntOffset(0, if (isDragged) dragY.roundToInt() else 0) }
                        .then(if (isDragged) Modifier.shadow(8.dp) else Modifier),
                ) {
                    QueueRow(
                        entry, isCurrent = false,
                        onClick = { onPlayAt(entry.index) },
                        onRemove = { onRemoveAt(entry.index) },
                        handle = Modifier.pointerInput(entry.index) {
                            detectDragGestures(
                                onDragStart = { dragging = entry.index; dragY = 0f },
                                onDragEnd = {
                                    val from = entry.index
                                    // Upcoming songs can only move among upcoming songs.
                                    val to = (from + (dragY / rowHeight).roundToInt())
                                        .coerceIn(currentIndex + 1, last)
                                    dragging = null
                                    dragY = 0f
                                    move(from, to)
                                },
                                onDragCancel = { dragging = null; dragY = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragY += amount.y
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    handle: Modifier?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GroovioColors.Background)
            .clickable(enabled = !isCurrent, onClickLabel = "Play ${entry.title}", onClick = onClick)
            .padding(start = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                color = if (isCurrent) GroovioColors.Accent else GroovioColors.Text,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            entry.artist?.let {
                Text(it, color = GroovioColors.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove ${entry.title} from queue") }
        }
        if (handle != null) {
            Icon(
                Icons.Default.DragHandle, "Drag to reorder ${entry.title}",
                tint = GroovioColors.TextMuted,
                modifier = handle.padding(12.dp).size(24.dp),
            )
        }
    }
}
