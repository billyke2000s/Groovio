package com.groovio.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.FolderOpen
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
import kotlinx.coroutines.launch
import com.groovio.app.data.TrackEntity
import com.groovio.app.ui.motion.groovioCombinedClickable
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
    versionName: String = "",
    updateMessage: String? = null,
    onCheckUpdates: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenFiles: () -> Unit = {},
    onOpenSpoken: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenCollection: (com.groovio.app.data.RecentCollection) -> Unit = {},
    onLongPressCollection: (com.groovio.app.data.RecentCollection) -> Unit = {},
) {
    if (view != LibraryView.ROOT) BackHandler { onViewChange(LibraryView.ROOT) }
    val playlistId = LibraryView.playlistId(view)
    when {
        view == LibraryView.LIKED -> LikedSongs(vm, currentMediaId, onPlay, onMore) { onViewChange(LibraryView.ROOT) }
        playlistId != null -> PlaylistDetail(vm, playlistId, currentMediaId, onPlay, onMore) { onViewChange(LibraryView.ROOT) }
        else -> LibraryRoot(vm, currentMediaId, onPlay, onViewChange, onMore, versionName, updateMessage, onCheckUpdates, onOpenDownloads, onOpenFiles, onOpenSpoken, onOpenArtist, onSearch, onOpenCollection, onLongPressCollection)
    }
}

private const val FILTER_PLAYLISTS = "playlists"
private const val FILTER_PODCASTS = "podcasts"
private const val FILTER_ALBUMS = "albums"
private const val FILTER_ARTISTS = "artists"
private const val FILTER_AUDIOBOOKS = "audiobooks"

private data class LibraryEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val artwork: String? = null,
    val icon: ImageVector = Icons.AutoMirrored.Filled.QueueMusic,
    val onOpen: () -> Unit,
    val onLongPress: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryRoot(
    vm: LibraryViewModel,
    currentMediaId: String?,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onViewChange: (String) -> Unit,
    onMore: (TrackEntity) -> Unit,
    versionName: String,
    updateMessage: String?,
    onCheckUpdates: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSpoken: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenCollection: (com.groovio.app.data.RecentCollection) -> Unit,
    onLongPressCollection: (com.groovio.app.data.RecentCollection) -> Unit,
) {
    val liked by vm.favourites.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.groovio.app.GroovioApplication
    val recentCollections by app.collectionHistory.recent.collectAsStateWithLifecycle()
    val followed by app.spoken.followed.collectAsStateWithLifecycle()
    val savedSpoken by app.spoken.saved.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var alphabetical by rememberSaveable { mutableStateOf(false) }

    // These are real saved collections and listening contexts, not the individual song history.
    // Keep the pinned Liked Songs first in Recents and avoid repeating playlists already in history.
    val likedEntry = LibraryEntry("liked", "Liked songs", "Playlist · ${liked.size} songs",
        icon = Icons.Default.Favorite, onOpen = { onViewChange(LibraryView.LIKED) },
        onLongPress = { onLongPressCollection(com.groovio.app.data.RecentCollection(
            "liked", "Liked songs", "Playlist", liked.firstOrNull()?.artworkUri, liked)) })
    val filesEntry = LibraryEntry("local-files", "Your music", "Downloaded songs and music on this phone",
        icon = Icons.Default.FolderOpen, onOpen = onOpenFiles)
    val collectionEntries = recentCollections.map { collection ->
        LibraryEntry("recent:${collection.id}", collection.title, collection.kind,
            collection.artworkUri ?: collection.tracks.firstOrNull()?.artworkUri,
            onOpen = { onOpenCollection(collection) },
            onLongPress = { onLongPressCollection(collection) })
    }
    val playlistEntries = playlists.map { playlist ->
        LibraryEntry("playlist:${playlist.id}", playlist.name, "Playlist · ${playlist.trackCount} songs",
            playlist.artworkUri, onOpen = { onViewChange(LibraryView.playlist(playlist.id)) },
            onLongPress = { onLongPressCollection(com.groovio.app.data.RecentCollection(
                "playlist:${playlist.id}", playlist.name, "Playlist", playlist.artworkUri,
                emptyList(), playlistId = playlist.id)) })
    }
    val podcastEntries = followed.filter { it.kind == "Podcasts" }.map { c ->
        LibraryEntry("podcast:${c.album.url}", c.album.title, "Podcast · Following",
            c.album.thumbnailUrl, Icons.Default.Headphones, { onOpenSpoken("Podcasts") })
    } + savedSpoken.filter { it.kind == "Podcasts" }.map { item ->
        LibraryEntry("episode:${item.track.id}", item.track.title, "Saved episode",
            item.track.thumbnailUrl, Icons.Default.Headphones, { onOpenSpoken("Podcasts") })
    }
    val audiobookEntries = followed.filter { it.kind == "Audiobooks" }.map { c ->
        LibraryEntry("book:${c.album.url}", c.album.title, "Saved audiobook",
            c.album.thumbnailUrl, Icons.Default.MenuBook, { onOpenSpoken("Audiobooks") })
    } + savedSpoken.filter { it.kind == "Audiobooks" }.map { item ->
        LibraryEntry("recording:${item.track.id}", item.track.title, "Saved recording",
            item.track.thumbnailUrl, Icons.Default.MenuBook, { onOpenSpoken("Audiobooks") })
    }
    val artistEntries = (liked + recent).mapNotNull { it.artist?.takeIf(String::isNotBlank) }
        .distinct().map { name ->
            LibraryEntry("artist:$name", name, "Artist", icon = Icons.Default.Person,
                onOpen = { onOpenArtist(name) })
        }
    val allEntries = buildList {
        add(likedEntry)
        add(filesEntry)
        addAll(collectionEntries)
        addAll(playlistEntries.filterNot { p -> recentCollections.any { c -> c.playlistId != null && p.key == "playlist:${c.playlistId}" } })
        addAll(podcastEntries.filter { it.key.startsWith("podcast:") })
        addAll(audiobookEntries.filter { it.key.startsWith("book:") })
    }.distinctBy { it.key }
    val displayed = when (filter) {
        FILTER_PLAYLISTS -> listOf(likedEntry) + playlistEntries
        FILTER_PODCASTS -> podcastEntries
        FILTER_ALBUMS -> collectionEntries.filter { entry ->
            recentCollections.any { c -> entry.key == "recent:${c.id}" && c.kind == "Album" }
        }
        FILTER_ARTISTS -> artistEntries
        FILTER_AUDIOBOOKS -> audiobookEntries
        else -> allEntries
    }.distinctBy { it.key }.let { entries ->
        if (alphabetical) entries.sortedBy { it.title.lowercase() } else entries
    }
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 3 else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = if (grid) 12.dp else 0.dp, end = if (grid) 12.dp else 0.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 0.dp),
    ) {
        item(span = full) {
            Row(
                Modifier.fillMaxWidth().padding(start = if (grid) 8.dp else 20.dp, end = 8.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your Library", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search Groovio") }
            }
        }
        item(span = full) {
            LazyRow(contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(
                    FILTER_PLAYLISTS to "Playlists", FILTER_PODCASTS to "Podcasts",
                    FILTER_ALBUMS to "Albums", FILTER_ARTISTS to "Artists",
                    FILTER_AUDIOBOOKS to "Audiobooks",
                )) { (key, title) ->
                    FilterChip(selected = filter == key, onClick = { filter = if (filter == key) null else key },
                        label = { Text(title) }, shape = RoundedCornerShape(40.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = GroovioColors.SurfaceHigh,
                            selectedContainerColor = GroovioColors.Accent,
                            selectedLabelColor = GroovioColors.OnAccent))
                }
            }
        }
        item(span = full) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { alphabetical = !alphabetical }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = GroovioColors.Text)
                    Text(if (alphabetical) " Alphabetical" else " Recents", color = GroovioColors.Text)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenDownloads) {
                    Icon(Icons.Default.Download, "Open downloads")
                }
                IconButton(onClick = { grid = !grid }) {
                    Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        if (grid) "Show as list" else "Show as grid")
                }
            }
        }
        if (displayed.isEmpty()) item(span = full) {
            Text(when (filter) {
                FILTER_PODCASTS -> "Follow a podcast or save an episode to see it here."
                FILTER_ALBUMS -> "Albums you listen to will appear here."
                FILTER_ARTISTS -> "Artists from your listening and liked songs will appear here."
                FILTER_AUDIOBOOKS -> "Save an audiobook to see it here."
                else -> "Your saved music will appear here."
            }, color = GroovioColors.TextMuted, modifier = Modifier.padding(20.dp))
        }
        items(displayed, key = { it.key }, span = { if (grid) GridItemSpan(1) else full() }) { entry ->
            Box(Modifier.animateItem()) {
                if (grid) {
                    CollectionTile(entry.icon, entry.title, entry.subtitle, entry.artwork, entry.onOpen, entry.onLongPress)
                } else {
                    CollectionRow(entry.icon, entry.title, entry.subtitle, entry.artwork, entry.onOpen, entry.onLongPress)
                }
            }
        }
        item(span = full) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Groovio $versionName", color = GroovioColors.TextMuted, fontSize = 13.sp)
                TextButton(onClick = onCheckUpdates) { Text("Check for updates", color = GroovioColors.Accent) }
                updateMessage?.let { Text(it, color = GroovioColors.TextMuted, fontSize = 13.sp) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionTile(
    icon: ImageVector, title: String, subtitle: String, artwork: String? = null,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().groovioCombinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(GroovioColors.SurfaceHigh, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { if (artwork != null) coil.compose.AsyncImage(model = artwork, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(icon, contentDescription = null, tint = GroovioColors.Accent, modifier = Modifier.size(40.dp)) }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, color = GroovioColors.TextMuted, fontSize = 12.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionRow(
    icon: ImageVector, title: String, subtitle: String, artwork: String? = null,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().groovioCombinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(GroovioColors.SurfaceHigh, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { if (artwork != null) coil.compose.AsyncImage(model = artwork, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(icon, contentDescription = null, tint = GroovioColors.Accent) }
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
        DownloadCollectionButton(liked) { onPlay(liked, 0) }
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
        DownloadCollectionButton(tracks) { onPlay(tracks, 0) }
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
