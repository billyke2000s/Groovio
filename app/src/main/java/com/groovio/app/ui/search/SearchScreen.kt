package com.groovio.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.groovio.app.online.OnlineTrack
import com.groovio.app.playback.PlaybackText
import com.groovio.app.ui.theme.GroovioColors

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    currentMediaId: String?,
    onPlay: (List<OnlineTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val input by vm.input.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier.fillMaxSize()) {
        Text(
            "Search",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = input,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Songs or artists") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (input.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Default.Close, "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GroovioColors.Surface,
                unfocusedContainerColor = GroovioColors.Surface,
                focusedBorderColor = GroovioColors.Accent,
                unfocusedBorderColor = GroovioColors.Surface,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        when {
            state.loading && state.results.isEmpty() -> Centered {
                CircularProgressIndicator(color = GroovioColors.Accent)
            }
            state.error != null -> Centered {
                Text(state.error!!, color = GroovioColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::retry) { Text("Try again") }
            }
            state.query.isEmpty() -> Centered {
                Text("Find songs and play them instantly.", color = GroovioColors.TextMuted)
            }
            state.results.isEmpty() -> Centered {
                Text("No songs found for “${state.query}”.", color = GroovioColors.TextMuted)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.results, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        playing = currentMediaId == "yt:${track.id}",
                        onClick = {
                            keyboard?.hide()
                            onPlay(state.results, index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun TrackRow(track: OnlineTrack, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Play ${track.title}", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(GroovioColors.ArtworkFallback),
        )
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (playing) GroovioColors.Accent else GroovioColors.Text,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(track.artist, track.durationMs?.let(PlaybackText::time))
                .joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, color = GroovioColors.TextMuted, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
