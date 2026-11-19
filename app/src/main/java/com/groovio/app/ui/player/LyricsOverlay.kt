package com.groovio.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.groovio.app.lyrics.LrcLyrics
import com.groovio.app.lyrics.LyricsDocument
import com.groovio.app.lyrics.LyricsLookup
import com.groovio.app.lyrics.LyricsRepository
import com.groovio.app.lyrics.LyricsTrack
import com.groovio.app.playback.PlaybackText
import com.groovio.app.playback.PlayerUiState
import com.groovio.app.ui.theme.GroovioColors
import java.io.File

/** Full-player companion: timed lines when available, otherwise readable plain lyrics. */
@Composable
fun LyricsOverlay(
    state: PlayerUiState,
    positionMs: Long,
    tint: Color,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { LyricsRepository(File(context.filesDir, "lyrics")) }
    val song = remember(state.currentMediaId, state.title, state.artist, state.durationMs) {
        LyricsTrack(state.currentMediaId.orEmpty(), state.title, state.artist, state.durationMs)
    }
    var retry by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<LyricsLookup?>(null) }
    LaunchedEffect(song, retry) {
        result = null
        result = repository.load(song)
    }
    val document = (result as? LyricsLookup.Found)?.document
    val lines = document?.synced.orEmpty()
    val selectedLine = LrcLyrics.activeIndex(lines, positionMs)
    val scroll = rememberLazyListState()
    LaunchedEffect(song.mediaId) { scroll.scrollToItem(0) }
    LaunchedEffect(song.mediaId, selectedLine) {
        if (selectedLine >= 0 && selectedLine < lines.size) {
            scroll.animateScrollToItem(selectedLine)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint, GroovioColors.Background)))
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LYRICS", style = MaterialTheme.typography.labelLarge,
                        color = GroovioColors.Accent)
                    Text(state.title, color = GroovioColors.Text, fontWeight = FontWeight.Bold,
                        fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.artist ?: "Unknown artist", color = GroovioColors.TextMuted,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close lyrics",
                        tint = GroovioColors.Text)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val status = result) {
                    null -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = GroovioColors.Accent,
                    )
                    is LyricsLookup.Missing -> Message(
                        "No matching lyrics found",
                        "Lyrics are not available for this recording, or its song metadata couldn't be matched.",
                        Modifier.align(Alignment.Center),
                    )
                    is LyricsLookup.Failed -> Column(Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Message("Lyrics unavailable", status.message)
                        TextButton(onClick = { retry++ }) { Text("Try again") }
                    }
                    is LyricsLookup.Found -> {
                        when {
                            document?.instrumental == true -> Message(
                                "Instrumental",
                                "This recording has no sung lyrics.",
                                Modifier.align(Alignment.Center),
                            )
                            lines.isNotEmpty() -> LazyColumn(
                                state = scroll,
                                contentPadding = PaddingValues(vertical = 68.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                itemsIndexed(lines) { index, line ->
                                    val active = index == selectedLine
                                    Text(
                                        line.text,
                                        fontSize = if (active) 29.sp else 24.sp,
                                        lineHeight = if (active) 36.sp else 32.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (active) GroovioColors.Accent
                                            else GroovioColors.Text.copy(alpha = if (index < selectedLine) 0.48f else 0.78f),
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable(onClickLabel = "Seek to " + PlaybackText.time(line.timeMs)) {
                                                onSeek(line.timeMs)
                                            }
                                            .padding(vertical = 4.dp),
                                    )
                                }
                            }
                            !document?.plain.isNullOrBlank() -> Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            ) {
                                Text(document?.plain.orEmpty(),
                                    color = GroovioColors.Text,
                                    style = MaterialTheme.typography.headlineSmall,
                                    lineHeight = 33.sp,
                                )
                            }
                            else -> Message("No lyrics available", "This song has no readable lyric text.",
                                Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (lines.isNotEmpty()) "Tap a line to jump to that moment" else "Lyrics may not be available for every track",
                color = GroovioColors.TextMuted, fontSize = 12.sp,
            )
            Text("Lyrics provided by LRCLIB · Cached privately after loading",
                color = GroovioColors.TextMuted.copy(alpha = 0.85f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun Message(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = GroovioColors.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(detail, color = GroovioColors.TextMuted, fontSize = 15.sp)
    }
}
