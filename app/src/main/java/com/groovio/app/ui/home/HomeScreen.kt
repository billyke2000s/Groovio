package com.groovio.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovio.app.ui.player.GroovioArtwork
import com.groovio.app.data.PlaylistSummary
import com.groovio.app.data.TrackEntity
import com.groovio.app.data.TrackMapping
import com.groovio.app.online.OnlineAlbum
import com.groovio.app.online.OnlineTrack
import com.groovio.app.ui.browse.AlbumCard
import com.groovio.app.ui.motion.groovioCombinedClickable
import com.groovio.app.ui.theme.GroovioColors
import java.time.LocalTime

/** One quick-access tile at the top of Home (Spotify-style two-column grid). */
private data class Tile(
    val key: String,
    val title: String,
    val icon: ImageVector?,
    val artworkUri: String?,
    val artworkUris: List<String> = emptyList(),
    val onLongClick: (() -> Unit)? = null,
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
    onMore: (TrackEntity) -> Unit,
    chart: com.groovio.app.online.SpotifyCollection? = null,
    onPlayChart: (com.groovio.app.online.SpotifyCollection) -> Unit = {},
    onDownloadChart: (com.groovio.app.online.SpotifyCollection) -> Unit = {},
    downloadsEnabled: Boolean = false,
    recentCollections: List<com.groovio.app.data.RecentCollection> = emptyList(),
    onOpenCollection: (com.groovio.app.data.RecentCollection) -> Unit = {},
    onOpenMix: (PersonalMix) -> Unit = {},
    onPlaylistLongPress: (com.groovio.app.data.RecentCollection, String) -> Unit = { _, _ -> },
    hiddenHomeCards: Set<String> = emptySet(),
    personalMixes: List<PersonalMix> = emptyList(),
    onSaveMix: (PersonalMix) -> Unit = {},
    mixNotice: String? = null,
    mix: RelatedShelf? = null,
    related: RelatedShelf? = null,
    discover: List<DiscoverShelf> = emptyList(),
    onOpenAlbum: (OnlineAlbum) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    update: UpdateState? = null,
    onUpdate: () -> Unit = {},
    onPlayRelated: (List<OnlineTrack>, Int) -> Unit = { _, _ -> },
    filter: String = "All",
    onFilter: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val homeCollections = visibleCollectionsForHome(recentCollections, filter)
    val firstRun = recent.isEmpty() && likedCount == 0 && playlists.isEmpty() && homeCollections.isEmpty()
    val tiles = buildList {
        // Always-visible entry point in addition to the header folder icon.
        add(Tile("local-files", "Your music", Icons.Default.FolderOpen, null, onClick = onOpenFiles))
        homeCollections.filter {
            HomeCardVisibility.visible(hiddenHomeCards, "quick", it.id)
        }.take(5).forEach { c ->
            add(Tile("collection:${c.id}", c.title, Icons.AutoMirrored.Filled.QueueMusic, c.artworkUri,
                artworkUris = c.tracks.mapNotNull { it.artworkUri }.distinct().take(4),
                onLongClick = { onPlaylistLongPress(c, "quick") }) { onOpenCollection(c) })
        }
        if (size < 6 && likedCount > 0 && none { it.key == "collection:liked" }) add(Tile("liked", "Liked songs", Icons.Default.Favorite, null, onClick = onOpenLiked))
        playlists.filter { p ->
            homeCollections.none { it.playlistId == p.id } &&
                HomeCardVisibility.visible(hiddenHomeCards, "quick", "playlist:${p.id}")
        }.take(6 - size).forEach { p ->
            val collection = com.groovio.app.data.RecentCollection(
                "playlist:${p.id}", p.name, "Playlist", p.artworkUri, emptyList(), playlistId = p.id)
            add(Tile("p${p.id}", p.name, Icons.AutoMirrored.Filled.QueueMusic, p.artworkUri,
                onLongClick = { onPlaylistLongPress(collection, "quick") }) { onOpenPlaylist(p.id) })
        }
    }.take(6)

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(greeting(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onOpenFiles) {
                    Icon(Icons.Default.FolderOpen, "Your music and downloads")
                }
                androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, "Settings")
                }
            }
        }

        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Music", "Podcasts", "Audiobooks")) { label ->
                    androidx.compose.material3.FilterChip(selected = filter == label, onClick = { onFilter(label) },
                        label = { Text(label) }, shape = androidx.compose.foundation.shape.CircleShape, border = null, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = GroovioColors.SurfaceHigh, selectedContainerColor = GroovioColors.Accent, selectedLabelColor = GroovioColors.OnAccent))
                }
            }
        }
        if (update != null) item { UpdateBanner(update, onUpdate) }

        // New users get the welcome card, then the discovery shelves below.
        if (firstRun) item { Welcome(onSearch, onOpenFiles) }

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

        val visibleJump = homeCollections.filter {
            HomeCardVisibility.visible(hiddenHomeCards, "jump", it.id)
        }
        if (visibleJump.isNotEmpty()) {
            item { Shelf("Jump back in") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visibleJump, key = { it.id }) { collection ->
                        SquareCard(collection.title, collection.kind, collection.artworkUri, false,
                            onLongClick = { onPlaylistLongPress(collection, "jump") },
                            artworkUris = if (collection.artworkUri == null) collection.tracks.mapNotNull { it.artworkUri }.distinct().take(4) else emptyList()) {
                            onOpenCollection(collection)
                        }
                    }
                }
            }
        }
        if (personalMixes.isNotEmpty()) {
            personalMixes.filter {
                HomeCardVisibility.visible(hiddenHomeCards, "mixes", "mix:${it.id}")
            }.groupBy { when { it.title.startsWith("Daily Mix") -> "Made for you"; it.title == "Discovery mix" -> "Discover more"; else -> "Your top mixes" } }.forEach { (title, mixes) ->
            item { Shelf(title) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mixes, key = { it.id }) { mix ->
                        Column {
                            val collection = com.groovio.app.data.RecentCollection(
                                "mix:${mix.id}", mix.title, "Mix", mix.tracks.firstOrNull()?.thumbnailUrl,
                                mix.tracks.map(TrackMapping::fromOnline))
                            SquareCard(mix.title, mix.reason, mix.tracks.firstOrNull()?.thumbnailUrl, false,
                                onLongClick = { onPlaylistLongPress(collection, "mixes") },
                                artworkUris = mix.tracks.mapNotNull { it.thumbnailUrl }.distinct().take(4)) { onOpenMix(mix) }
                            androidx.compose.material3.TextButton(onClick = { onSaveMix(mix) }) { Text("Save playlist") }
                        }
                    }
                }
            }
        }
        }
        mixNotice?.let { item { Text(it, color = GroovioColors.Accent, modifier = Modifier.padding(16.dp)) } }
        if (mix != null && personalMixes.isEmpty()) {
            item { Shelf("Made from your favourites") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(mix.tracks, key = { _, t -> t.id }) { i, t ->
                        SquareCard(t.title, t.artist, t.thumbnailUrl, currentMediaId == "yt:${t.id}",
                            onLongClick = { onMore(com.groovio.app.data.TrackMapping.fromOnline(t)) }) { onPlayRelated(mix.tracks, i) }
                    }
                }
            }
        }
        if (related != null) {
            item { Shelf("More like ${related.seedTitle}") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(related.tracks, key = { _, t -> "rel" + t.id }) { i, t ->
                        SquareCard(t.title, t.artist, t.thumbnailUrl, currentMediaId == "yt:${t.id}",
                            onLongClick = { onMore(TrackMapping.fromOnline(t)) }) { onPlayRelated(related.tracks, i) }
                    }
                }
            }
        }
        val visiblePlaylists = playlists.filter {
            HomeCardVisibility.visible(hiddenHomeCards, "playlists", "playlist:${it.id}")
        }
        if (visiblePlaylists.isNotEmpty()) {
            item { Shelf("Your playlists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visiblePlaylists, key = { it.id }) { p ->
                        val collection = com.groovio.app.data.RecentCollection(
                            "playlist:${p.id}", p.name, "Playlist", p.artworkUri, emptyList(), playlistId = p.id)
                        SquareCard(p.name, "${p.trackCount} songs", p.artworkUri, false, Icons.AutoMirrored.Filled.QueueMusic,
                            onLongClick = { onPlaylistLongPress(collection, "playlists") }) {
                            onOpenPlaylist(p.id)
                        }
                    }
                }
            }
        }
        discover.forEach { shelf ->
            item(key = "dt-" + shelf.title) { Shelf(shelf.title) }
            item(key = "dr-" + shelf.title) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (shelf.albums.isNotEmpty()) {
                        items(shelf.albums, key = { it.url }) { a -> AlbumCard(a) { onOpenAlbum(a) } }
                    } else {
                        itemsIndexed(shelf.tracks, key = { _, t -> t.id }) { i, t ->
                            SquareCard(t.title, t.artist, t.thumbnailUrl, currentMediaId == "yt:${t.id}",
                                onLongClick = { onMore(TrackMapping.fromOnline(t)) }) { onPlayRelated(shelf.tracks, i) }
                        }
                    }
                }
            }
        }
        if (chart != null) item {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("UK top songs", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Apple chart · matched to YouTube for playback", color = GroovioColors.TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(onClick = { onPlayChart(chart) }) { Text("Play") }
                    if (downloadsEnabled) androidx.compose.material3.OutlinedButton(onClick = { onDownloadChart(chart) }) { Text("Download & play") }
                }
                chart.tracks.take(5).forEachIndexed { i, t -> Text("${i + 1}. ${t.title} · ${t.artist.orEmpty()}", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(tile: Tile, modifier: Modifier) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(6.dp)).background(GroovioColors.SurfaceHigh)
            .groovioCombinedClickable(onClickLabel = "Open ${tile.title}",
                onLongClickLabel = "Playlist options", onLongClick = tile.onLongClick,
                onClick = tile.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).background(GroovioColors.ArtworkFallback), contentAlignment = Alignment.Center) {
            if (tile.artworkUri != null) {
                GroovioArtwork(model = tile.artworkUri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else if (tile.artworkUris.size >= 4) {
                Column(Modifier.fillMaxSize()) {
                    tile.artworkUris.take(4).chunked(2).forEach { pair ->
                        Row(Modifier.weight(1f)) {
                            pair.forEach { art ->
                                GroovioArtwork(model = art, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxSize())
                            }
                        }
                    }
                }
            } else if (tile.artworkUris.isNotEmpty()) {
                GroovioArtwork(model = tile.artworkUris.first(), contentDescription = null, contentScale = ContentScale.Crop,
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SquareCard(
    title: String,
    subtitle: String?,
    artworkUri: String?,
    playing: Boolean,
    icon: ImageVector = Icons.Default.MusicNote,
    onLongClick: (() -> Unit)? = null,
    artworkUris: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    Column(Modifier.width(140.dp).groovioCombinedClickable(onClickLabel = "Play $title", onLongClick = onLongClick, onClick = onClick)) {
        Box(Modifier.size(140.dp).clip(RoundedCornerShape(6.dp)).background(GroovioColors.ArtworkFallback),
            contentAlignment = Alignment.Center) {
            if (artworkUris.size >= 4) {
                Column(Modifier.fillMaxSize()) {
                    artworkUris.take(4).chunked(2).forEach { pair ->
                        Row(Modifier.weight(1f)) { pair.forEach { art ->
                            GroovioArtwork(model = art, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxSize())
                        } }
                    }
                }
            } else if (artworkUri != null) {
                GroovioArtwork(model = artworkUri, contentDescription = null, contentScale = ContentScale.Crop,
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

/** In-app update banner: download progress inside Groovio, then Android's install screen. */
@Composable
private fun UpdateBanner(state: UpdateState, onUpdate: () -> Unit) {
    val (title, detail, action) = when (state) {
        is UpdateState.Available -> Triple("Update available", "Groovio ${state.update.version}", "Update")
        is UpdateState.Downloading -> Triple(
            "Downloading update…",
            if (state.progress >= 0) "${(state.progress * 100).toInt()}%" else "Groovio ${state.update.version}",
            null,
        )
        is UpdateState.NeedsPermission -> Triple("Allow Groovio to install updates", "Turn on the switch, then come back", "Allow")
        is UpdateState.Ready -> Triple("Update downloaded", "Groovio ${state.update.version}", "Install")
        is UpdateState.Failed -> Triple("Update failed", state.message, "Retry")
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .background(GroovioColors.Accent, RoundedCornerShape(12.dp))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = GroovioColors.OnAccent, fontWeight = FontWeight.Bold)
                Text(detail, color = GroovioColors.OnAccent, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state is UpdateState.Available || state is UpdateState.Ready) {
                    state.update.changes?.lineSequence()?.firstOrNull()?.let { summary ->
                        Text(summary, color = GroovioColors.OnAccent, fontSize = 12.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (action != null) {
                androidx.compose.material3.TextButton(onClick = onUpdate) {
                    Text(action, color = GroovioColors.OnAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (state is UpdateState.Downloading) {
            val p = state.progress
            if (p >= 0) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 8.dp),
                    color = GroovioColors.OnAccent, trackColor = GroovioColors.Accent.copy(alpha = 0.4f),
                )
            } else {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 8.dp),
                    color = GroovioColors.OnAccent,
                )
            }
        }
    }
}

/** Music is a real category filter, not just a highlighted Home tab. */
internal fun visibleCollectionsForHome(
    collections: List<com.groovio.app.data.RecentCollection>,
    filter: String,
): List<com.groovio.app.data.RecentCollection> =
    if (filter == "Music") collections.filterNot { it.kind == "Show" || it.kind == "Audiobook" }
    else collections
