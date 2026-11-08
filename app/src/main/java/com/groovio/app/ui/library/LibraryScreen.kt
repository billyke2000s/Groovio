package com.groovio.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.groovio.app.data.TrackEntity
import com.groovio.app.ui.theme.GroovioColors

/** Library sub-view, kept as a string so it survives configuration changes. */
object LibraryView {
    const val ROOT = "root"
    const val LIKED = "liked"
    fun playlist(id: Long) = "playlist:$id"
    fun playlistId(view: String): Long? = view.removePrefix("playlist:").takeIf { view.startsWith("playlist:") }?.toLongOrNull()
}

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    view: String,
    onViewChange: (String) -> Unit,
    currentMediaId: String?,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onMore: (TrackEntity) -> Unit,
) {
    if (view != LibraryView.ROOT) BackHandler { onViewChange(LibraryView.ROOT) }
    val playlistId = LibraryView.playlistId(view)
    when {
        view == LibraryView.LIKED -> LikedSongs(vm, currentMediaId, onPlay, onMore) { onViewChange(LibraryView.ROOT) }
        playlistId != null -> PlaylistDetail(vm, playlistId, currentMediaId, onPlay, onMore) { onViewChange(LibraryView.ROOT) }
        else -> LibraryRoot(vm, currentMediaId, onPlay, onViewChange, onMore)
    }
}

private const val FILTER_PLAYLISTS = "playlists"
private const val FILTER_RECENT = "recent"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryRoot(
    vm: LibraryViewModel,
    currentMediaId: String?,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onViewChange: (String) -> Unit,
    onMore: (TrackEntity) -> Unit,
) {
    val liked by vm.favourites.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    // Spotify-style filter chips: null shows everything.
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val showPlaylists = filter == null || filter == FILTER_PLAYLISTS
    val showRecent = filter == null || filter == FILTER_RECENT

    // Spotify-style list/grid toggle and sort order for playlists.
    var grid by rememberSaveable { mutableStateOf(false) }
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    val sortedPlaylists = if (alphabetical) playlists.sortedBy { it.name.lowercase() } else playlists
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 3 else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (grid) 12.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 0.dp),
    ) {
        item(span = full) {
            Row(
                Modifier.fillMaxWidth().padding(start = if (grid) 8.dp else 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your Library", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "New playlist") }
            }
        }
        item(span = full) {
            Row(Modifier.padding(horizontal = if (grid) 4.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(FILTER_PLAYLISTS to "Playlists", FILTER_RECENT to "Recently played").forEach { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = if (filter == key) null else key },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GroovioColors.Accent,
                            selectedLabelColor = GroovioColors.OnAccent,
                        ),
                    )
                }
            }
        }
        if (showPlaylists) item(span = full) {
            Row(
                Modifier.fillMaxWidth().padding(start = if (grid) 0.dp else 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { alphabetical = !alphabetical }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = GroovioColors.Text)
                    Text(if (alphabetical) "  Alphabetical" else "  Recents", color = GroovioColors.Text)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { grid = !grid }) {
                    Icon(
                        if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        if (grid) "Show as list" else "Show as grid",
                    )
                }
            }
        }
        if (showPlaylists) item(key = "liked") {
            if (grid) {
                CollectionTile(Icons.Default.Favorite, "Liked songs", "${liked.size} songs") { onViewChange(LibraryView.LIKED) }
            } else {
                CollectionRow(Icons.Default.Favorite, "Liked songs", "${liked.size} songs") { onViewChange(LibraryView.LIKED) }
            }
        }
        if (showPlaylists) items(sortedPlaylists, key = { "p${it.id}" }) { p ->
            if (grid) {
                CollectionTile(Icons.AutoMirrored.Filled.QueueMusic, p.name, "${p.trackCount} songs") { onViewChange(LibraryView.playlist(p.id)) }
            } else {
                CollectionRow(Icons.AutoMirrored.Filled.QueueMusic, p.name, "Playlist · ${p.trackCount} songs") {
                    onViewChange(LibraryView.playlist(p.id))
                }
            }
        }
        if (showRecent) item(span = full) {
            Text(
                "Recently played",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if (grid) 8.dp else 20.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        if (showRecent && recent.isEmpty()) {
            item(span = full) {
                Text("Songs you play show up here.", color = GroovioColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }
        // Songs are always rows, even in grid mode (as in Spotify).
        if (showRecent) recent.forEachIndexed { i, track ->
            item(key = "r${track.mediaId}", span = full) {
                StoredTrackRow(track, currentMediaId == track.mediaId, onClick = { onPlay(recent, i) },
                    onLongClick = { onMore(track) }) {
                    IconButton(onClick = { onMore(track) }) { Icon(Icons.Default.MoreVert, "More options for ${track.title}") }
                }
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = vm::createPlaylist,
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun CollectionTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(GroovioColors.SurfaceHigh, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = GroovioColors.Accent, modifier = Modifier.size(40.dp)) }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, color = GroovioColors.TextMuted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun CollectionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(GroovioColors.SurfaceHigh, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = GroovioColors.Accent) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = GroovioColors.TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ListHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun LikedSongs(
    vm: LibraryViewModel,
    currentMediaId: String?,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onMore: (TrackEntity) -> Unit,
    onBack: () -> Unit,
) {
    val liked by vm.favourites.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ListHeader("Liked songs", onBack)
        if (liked.isEmpty()) {
            Text("Tap the heart in the player to save songs here.", color = GroovioColors.TextMuted,
                modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(liked, key = { _, t -> t.mediaId }) { i, track ->
                StoredTrackRow(track, currentMediaId == track.mediaId, onClick = { onPlay(liked, i) },
                    onLongClick = { onMore(track) }) {
                    IconButton(onClick = { vm.unlike(track) }) {
                        Icon(Icons.Default.Favorite, "Remove ${track.title} from Liked songs", tint = GroovioColors.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    vm: LibraryViewModel,
    id: Long,
    currentMediaId: String?,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onMore: (TrackEntity) -> Unit,
    onBack: () -> Unit,
) {
    val name by remember(id) { vm.playlistName(id) }.collectAsStateWithLifecycle(initialValue = "")
    val items by remember(id) { vm.playlistItems(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val tracks = items.map { it.track }

    Column(Modifier.fillMaxSize()) {
        ListHeader(name ?: "", onBack) {
            IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename playlist") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete playlist") }
        }
        if (items.isEmpty()) {
            Text("Add songs from the player with the playlist button.", color = GroovioColors.TextMuted,
                modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(items, key = { _, item -> item.position }) { i, item ->
                StoredTrackRow(item.track, currentMediaId == item.track.mediaId, onClick = { onPlay(tracks, i) },
                    onLongClick = { onMore(item.track) }) {
                    IconButton(onClick = { vm.removeFromPlaylist(id, item.position) }) {
                        Icon(Icons.Default.Close, "Remove ${item.track.title} from playlist")
                    }
                }
            }
        }
    }

    if (renaming) {
        PlaylistNameDialog(
            title = "Rename playlist",
            initial = name ?: "",
            confirmLabel = "Rename",
            onConfirm = { vm.renamePlaylist(id, it) },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${name ?: ""}”?") },
            text = { Text("The playlist is removed from this phone. Songs stay in Liked songs and history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deletePlaylist(id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
