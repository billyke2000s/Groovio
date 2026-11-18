package com.groovio.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.groovio.app.data.TrackEntity
import com.groovio.app.data.TrackMapping
import com.groovio.app.online.OnlineAlbum
import com.groovio.app.online.OnlineTrack
import com.groovio.app.ui.browse.AlbumCard
import com.groovio.app.online.SpotifyCollection
import com.groovio.app.playback.SpotifyImportProgress
import com.groovio.app.playback.PlaybackText
import com.groovio.app.ui.theme.GroovioColors

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    currentMediaId: String?,
    onPlay: (List<OnlineTrack>, Int) -> Unit,
    spotifyImport: SpotifyImportProgress?,
    onPlaySpotify: (SpotifyCollection) -> Unit,
    onOpenAlbum: (OnlineAlbum) -> Unit,
    onMore: (TrackEntity) -> Unit,
    onDownload: ((List<OnlineTrack>, Int) -> Unit)? = null,
    onDownloadSpotify: ((SpotifyCollection) -> Unit)? = null,
    downloadsEnabled: Boolean = false,
    recent: List<TrackEntity> = emptyList(),
    onPlayRecent: (List<TrackEntity>, Int) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val input by vm.input.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.groovio.app.GroovioApplication
    val online by app.connectivity.online.collectAsStateWithLifecycle()
    val offlineOnly by app.settings.offlineOnly.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxSize().then(Modifier.imePadding())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { back -> IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") } }
        Text(
            "Search",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
        )
        }
        OutlinedTextField(
            value = input,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Songs, artists or a Spotify link") },
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
            input.isBlank() && recent.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                item { Text("Last Groove", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                itemsIndexed(recent, key = { _, track -> track.mediaId }) { index, track ->
                    com.groovio.app.ui.library.StoredTrackRow(
                        track = track, playing = currentMediaId == track.mediaId,
                        onClick = { keyboard?.hide(); onPlayRecent(recent, index) },
                        onLongClick = { onMore(track) },
                    )
                }
            }
            state.loading && state.results.isEmpty() -> Centered {
                CircularProgressIndicator(color = GroovioColors.Accent)
            }
            state.error != null -> Centered {
                Text(state.error!!, color = GroovioColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::retry) { Text("Try again") }
            }
            state.spotify != null -> SpotifyResult(state.spotify!!, spotifyImport, onPlaySpotify, if (downloadsEnabled) onDownloadSpotify else null)
            state.query.isEmpty() && input.isBlank() && (!online || offlineOnly) -> Centered {
                Text("Search your saved songs here, or open Downloads above. Your rhythm can keep going offline.")
            }
            state.query.isEmpty() && input.isBlank() -> BrowseAll { vm.onQueryChange(it) }
            // Typing, waiting for the first results: show nothing rather than "no songs".
            state.query.isEmpty() -> Box(Modifier.fillMaxSize())
            state.results.isEmpty() && state.albums.isEmpty() && state.playlists.isEmpty() -> Centered {
                Text(if (!online || offlineOnly) "No saved songs found for “${state.query}”." else "No songs found for “${state.query}”.", color = GroovioColors.TextMuted)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (state.playlists.isNotEmpty()) item {
                    Text("Playlists", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.playlists, key = { it.url }) { playlist -> AlbumCard(playlist) { onOpenAlbum(playlist) } }
                    }
                }
                if (state.albums.isNotEmpty()) {
                    item {
                        Text("Albums", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.albums, key = { it.url }) { a -> AlbumCard(a) { onOpenAlbum(a) } }
                        }
                        Text("Songs", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                    }
                }
                itemsIndexed(state.results, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        playing = currentMediaId == "yt:${track.id}",
                        onClick = {
                            keyboard?.hide()
                            if (downloadsEnabled && onDownload != null) selected = index
                            else onPlay(state.results, index)
                        },
                        onMore = { onMore(TrackMapping.fromOnline(track)) },
                    )
                }
            }
        }
    }
    selected?.let { index ->
        val results = state.results
        val track = results.getOrNull(index)
        if (track != null) androidx.compose.material3.AlertDialog(
            onDismissRequest = { selected = null }, title = { Text(track.title) },
            text = { Text("Play now, or save this song for offline listening too.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                selected = null; onDownload?.invoke(listOf(track), 0)
            }) { Text("Download & play") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = {
                selected = null; onPlay(results, index)
            }) { Text("Play") } },
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(track: OnlineTrack, playing: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = "Play ${track.title}",
                onLongClickLabel = "More options",
                onLongClick = onMore,
                onClick = onClick,
            )
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
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
        IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "More options for ${track.title}") }
    }
}

@Composable
private fun SpotifyResult(
    collection: SpotifyCollection,
    progress: SpotifyImportProgress?,
    onPlay: (SpotifyCollection) -> Unit,
    onDownload: ((SpotifyCollection) -> Unit)?,
) {
    val kind = when (collection.type) {
        "album" -> "Album"
        "playlist" -> "Playlist"
        else -> "Song"
    }
    // Progress belongs to this collection only if the names match.
    val mine = progress?.takeIf { it.name == collection.name }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(GroovioColors.Surface, RoundedCornerShape(12.dp))
                    .padding(20.dp),
            ) {
                Text("From Spotify · $kind", color = GroovioColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(collection.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${collection.tracks.size} songs", color = GroovioColors.TextMuted, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                if (onDownload != null && (mine == null || mine.done)) {
                    androidx.compose.material3.OutlinedButton(onClick = { onDownload(collection) }) { Text("Download & play") }
                }
                when {
                    mine == null -> Button(onClick = { onPlay(collection) }) { Text("Play") }
                    !mine.done -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = GroovioColors.Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "Finding songs… ${mine.found + mine.missed} of ${mine.total}",
                            color = GroovioColors.TextMuted, fontSize = 14.sp,
                        )
                    }
                    else -> Column {
                        Text(
                            if (mine.missed == 0) "All ${mine.found} songs queued."
                            else "${mine.found} songs queued; ${mine.missed} couldn't be found.",
                            color = GroovioColors.TextMuted, fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onPlay(collection) }) { Text("Play again") }
                    }
                }
            }
        }
        items(collection.tracks.size) { i ->
            val t = collection.tracks[i]
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(t.artist, t.durationMs?.let(PlaybackText::time)).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, color = GroovioColors.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Genre and mood tiles shown before you type (like Spotify's "Browse all"). */
private val browseTiles = listOf(
    "Pop" to 0xFFE8115B, "Hip-hop" to 0xFFBA5D07, "Rock" to 0xFF8D67AB, "Dance" to 0xFF1E3264,
    "R&B" to 0xFFDC148C, "Indie" to 0xFF608108, "Chill" to 0xFF477D95, "Workout" to 0xFFE91429,
    "Party" to 0xFF8C1932, "Sleep" to 0xFF1E3264, "Focus" to 0xFF503750, "Country" to 0xFFB06239,
    "Latin" to 0xFFE1118C, "K-pop" to 0xFF148A08, "Jazz" to 0xFF2D46B9, "Classical" to 0xFF7D4B32,
)

@Composable
private fun BrowseAll(onPick: (String) -> Unit) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text("Browse all", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        items(browseTiles.size) { i ->
            val (label, color) = browseTiles[i]
            Box(
                Modifier
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color(color))
                    .clickable(onClickLabel = "Browse $label") { onPick("$label music") }
                    .padding(12.dp),
            ) {
                Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
