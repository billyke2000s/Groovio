package com.groovio.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovio.app.playback.PlayerUiState
import com.groovio.app.playback.QueueEntry
import com.groovio.app.ui.theme.GroovioColors

@Composable
fun QueueScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Queue", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (state.queue.isEmpty()) {
            Text(
                "Nothing queued.",
                color = GroovioColors.TextMuted,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.queue, key = { it.index }) { entry ->
                QueueRow(
                    entry = entry,
                    current = entry.index == state.currentIndex,
                    onClick = { onPlayAt(entry.index) },
                    onRemove = { onRemoveAt(entry.index) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(entry: QueueEntry, current: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (current) GroovioColors.Surface else GroovioColors.Background)
            .clickable(onClickLabel = "Play ${entry.title}", onClick = onClick)
            .padding(start = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                color = if (current) GroovioColors.Accent else GroovioColors.Text,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            entry.artist?.let {
                Text(it, color = GroovioColors.TextMuted, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, "Remove ${entry.title} from queue")
        }
    }
}
