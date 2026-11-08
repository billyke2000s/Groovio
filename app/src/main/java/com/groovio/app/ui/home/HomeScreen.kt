package com.groovio.app.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.groovio.app.data.PlaylistSummary
import com.groovio.app.data.TrackEntity
import com.groovio.app.ui.theme.GroovioColors
import java.time.LocalTime

/** One quick-access tile at the top of Home (Spotify-style two-column grid). */
private data class Tile(
    val key: String,
    val title: String,
    val icon: ImageVector?,
    val artworkUri: String?,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    recent: List<TrackEntity>,
    likedCount: Int,
    playlists: List<PlaylistSummary>,
    currentMediaId: String?,
    onPlayRecent: (List<TrackEntity>, Int) -> Unit,
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstRun = recent.isEmpty() && likedCount == 0 && playlists.isEmpty()
    val tiles = buildList {
        if (likedCount > 0) add(Tile("liked", "Liked songs", Icons.Default.Favorite, null, onOpenLiked))
        playlists.take(3).forEach { p ->
            add(Tile("p${p.id}", p.name, Icons.AutoMirrored.Filled.QueueMusic, null) { onOpenPlaylist(p.id) })
        }
        recent.take(6 - size).forEachIndexed { i, t ->
            add(Tile("r${t.mediaId}", t.title, null, t.artworkUri) { onPlayRecent(recent, i) })
        }
    }.take(6)

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(greeting(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onOpenFiles) {
                    Icon(Icons.Default.FolderOpen, "Play audio files from this phone")
                }
            }
        }

        if (firstRun) {
            item { Welcome(onSearch, onOpenFiles) }
            return@LazyColumn
        }

        // Two-column quick-access tiles.
        tiles.chunked(2).forEach { pair ->
            item(key = pair.joinToString { it.key }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { QuickTile(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { Shelf("Recently played") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(recent.take(20), key = { _, t -> t.mediaId }) { i, t ->
                        SquareCard(t.title, t.artist, t.artworkUri, currentMediaId == t.mediaId) { onPlayRecent(recent, i) }
                    }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            item { Shelf("Your playlists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        SquareCard(p.name, "${p.trackCount} songs", null, false, Icons.AutoMirrored.Filled.QueueMusic) {
                            onOpenPlaylist(p.id)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Welcome(onSearch: () -> Unit, onOpenFiles: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp)
            .background(GroovioColors.Surface, RoundedCornerShape(12.dp)).padding(20.dp),
    ) {
        Text("Find any song", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Search songs and artists, or paste a Spotify link. What you play and like shows up here.",
            color = GroovioColors.TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search")
            }
            OutlinedButton(onClick = onOpenFiles) { Text("Phone files") }
        }
    }
}

@Composable
private fun QuickTile(tile: Tile, modifier: Modifier) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(6.dp)).background(GroovioColors.SurfaceHigh)
            .clickable(onClickLabel = "Open ${tile.title}", onClick = tile.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).background(GroovioColors.ArtworkFallback), contentAlignment = Alignment.Center) {
            if (tile.artworkUri != null) {
                AsyncImage(model = tile.artworkUri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                Icon(tile.icon ?: Icons.Default.MusicNote, contentDescription = null, tint = GroovioColors.Accent)
            }
        }
        Text(tile.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun Shelf(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 10.dp))
}

@Composable
private fun SquareCard(
    title: String,
    subtitle: String?,
    artworkUri: String?,
    playing: Boolean,
    icon: ImageVector = Icons.Default.MusicNote,
    onClick: () -> Unit,
) {
    Column(Modifier.width(140.dp).clickable(onClickLabel = "Play $title", onClick = onClick)) {
        Box(Modifier.size(140.dp).clip(RoundedCornerShape(6.dp)).background(GroovioColors.ArtworkFallback),
            contentAlignment = Alignment.Center) {
            if (artworkUri != null) {
                AsyncImage(model = artworkUri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                Icon(icon, contentDescription = null, tint = GroovioColors.Accent, modifier = Modifier.size(48.dp))
            }
        }
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (playing) GroovioColors.Accent else GroovioColors.Text, modifier = Modifier.padding(top = 6.dp))
        subtitle?.let { Text(it, color = GroovioColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
