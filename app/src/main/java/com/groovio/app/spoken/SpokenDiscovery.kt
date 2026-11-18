package com.groovio.app.spoken

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.groovio.app.online.OnlineAlbum
import com.groovio.app.online.OnlineTrack
import com.groovio.app.playback.PlaybackText
import com.groovio.app.ui.player.rememberArtworkTint
import com.groovio.app.ui.theme.GroovioColors

/** Real catalogue data in a cover-led feed; no invented dates, descriptions or play counts. */
@Composable
fun SpokenDiscovery(
    kind: String,
    tracks: List<OnlineTrack>,
    collections: List<OnlineAlbum>,
    followed: List<SpokenCollection>,
    saved: List<SpokenItem>,
    downloadedIds: Set<String>,
    loading: Boolean,
    offline: Boolean,
    downloadsEnabled: Boolean,
    message: String?,
    query: String,
    onQuery: (String) -> Unit,
    onPlay: (OnlineTrack) -> Unit,
    onSave: (OnlineTrack) -> Unit,
    onRemove: (OnlineTrack) -> Unit,
    onDownload: (OnlineTrack) -> Unit,
    onOpen: (OnlineAlbum) -> Unit,
    onOpenSaved: (SpokenCollection) -> Unit,
) {
    val podcast = kind == "Podcasts"
    var followingOnly by rememberSaveable(kind) { mutableStateOf(false) }
    var searching by rememberSaveable(kind) { mutableStateOf(false) }
    var information by remember { mutableStateOf(false) }
    val myShows = followed.filter { it.kind == kind }
    val myItems = saved.filter { it.kind == kind }
    val savedIds = myItems.map { it.track.id }.toSet()
    val feed = if (offline) myItems.map { it.track } else tracks.distinctBy { it.id }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = followingOnly, onClick = { followingOnly = !followingOnly }, label = { Text(if (podcast) "Following" else "Saved") }, shape = CircleShape, border = null,
                    colors = FilterChipDefaults.filterChipColors(containerColor = GroovioColors.SurfaceHigh, selectedContainerColor = GroovioColors.Accent, selectedLabelColor = GroovioColors.OnAccent))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { searching = !searching }) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Search $kind") }
                IconButton(onClick = { information = true }) { Icon(Icons.Default.Info, "About $kind") }
            }
            androidx.compose.animation.AnimatedVisibility(searching) {
                OutlinedTextField(query, onQuery, singleLine = true,
                    placeholder = { Text(if (podcast) "Search shows and episodes" else "Search books and authors") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear spoken search") }
                    }, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth())
            }
        }
        if (myShows.isNotEmpty() || podcast) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(myShows, key = { it.album.url }) { show ->
                    Column(Modifier.width(88.dp).clickable(onClickLabel = "Open ${show.album.title}") { onOpenSaved(show) }) {
                        SpokenCover(show.album.thumbnailUrl, Modifier.size(88.dp))
                        Text(show.album.title, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item {
                    Column(Modifier.width(88.dp).clickable(onClickLabel = "Find more ${if (podcast) "shows" else "books"}") {
                        followingOnly = false; searching = true
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)).background(GroovioColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, tint = GroovioColors.Accent, modifier = Modifier.size(36.dp))
                        }
                        Text(if (podcast) "Find shows" else "Find books", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = GroovioColors.Accent) }
        message?.let { item { Text(it, color = GroovioColors.TextMuted, style = MaterialTheme.typography.bodyMedium) } }
        if (followingOnly) {
            item { Text(if (podcast) "Your shows" else "Your books", style = MaterialTheme.typography.titleLarge) }
            if (myShows.isEmpty() && myItems.isEmpty()) item {
                Text(if (podcast) "Follow a show to keep its episodes here." else "Save a book to keep it here.", color = GroovioColors.TextMuted)
            }
            items(myShows, key = { "following:${it.album.url}" }) { show ->
                SpokenCollectionCard(show.album, podcast = podcast, onClick = { onOpenSaved(show) })
            }
            if (myItems.isNotEmpty()) item { Text(if (podcast) "Saved episodes" else "Saved audiobooks", style = MaterialTheme.typography.titleMedium) }
            items(myItems, key = { "library:${it.track.id}" }) { item ->
                val track = item.track
                SpokenFeatureCard(track, podcast, true, "yt:${track.id}" in downloadedIds, item.positionMs,
                    downloadsEnabled && !offline, { onPlay(track) }, { onSave(track) }, { onRemove(track) }, { onDownload(track) })
            }
        } else {
            val inProgress = myItems.filter { it.positionMs > 0 }
            if (inProgress.isNotEmpty()) {
                item { Text("Continue listening", style = MaterialTheme.typography.titleLarge) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(inProgress, key = { it.track.id }) { item ->
                            Column(Modifier.width(136.dp).clickable(onClickLabel = "Resume ${item.track.title}") { onPlay(item.track) }) {
                                SpokenCover(item.track.thumbnailUrl, Modifier.size(136.dp))
                                Text(item.track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                                Text("${item.positionMs / 60_000} min played", color = GroovioColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item { Text(when { query.isNotBlank() -> "Search results"; offline -> "Saved for you"; podcast -> "Episodes you might like"; else -> "Audiobooks for you" }, style = MaterialTheme.typography.titleLarge) }
            if (feed.isEmpty() && !loading && message == null) item {
                Text(if (offline) "Your saved episodes and books will be here. Download some while connected." else "Find a show, author or book using search.", color = GroovioColors.TextMuted)
            }
            itemsIndexed(feed.take(3), key = { _, t -> "featured:${t.id}" }) { _, track ->
                SpokenFeatureCard(track, podcast, track.id in savedIds, "yt:${track.id}" in downloadedIds,
                    myItems.firstOrNull { it.track.id == track.id }?.positionMs ?: 0,
                    downloadsEnabled && !offline, { onPlay(track) }, { onSave(track) }, { onRemove(track) }, { onDownload(track) })
            }
            if (collections.isNotEmpty() && !offline) {
                item { Text(if (podcast) "Discover shows" else "Explore book collections", style = MaterialTheme.typography.titleLarge) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(collections.distinctBy { it.url }, key = { it.url }) { album ->
                            Column(Modifier.width(148.dp).clickable(onClickLabel = "Open ${album.title}") { onOpen(album) }) {
                                SpokenCover(album.thumbnailUrl, if (podcast) Modifier.size(148.dp) else Modifier.width(148.dp).height(196.dp))
                                Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                album.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = GroovioColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
            if (feed.size > 3) item { Text(if (podcast) "More episodes to explore" else "More books to explore", style = MaterialTheme.typography.titleLarge) }
            items(feed.drop(3), key = { "more:${it.id}" }) { track ->
                SpokenFeatureCard(track, podcast, track.id in savedIds, "yt:${track.id}" in downloadedIds,
                    myItems.firstOrNull { it.track.id == track.id }?.positionMs ?: 0,
                    downloadsEnabled && !offline, { onPlay(track) }, { onSave(track) }, { onRemove(track) }, { onDownload(track) })
            }
        }
    }
    if (information) AlertDialog(onDismissRequest = { information = false }, title = { Text("About $kind") },
        text = { Text("Audio comes from available public YouTube uploads. Episodes, book collections and chapters depend on the uploader. Saving keeps it in your library; downloading also saves the audio for offline listening.") },
        confirmButton = { TextButton(onClick = { information = false }) { Text("Got it") } })
}

@Composable
private fun SpokenFeatureCard(track: OnlineTrack, podcast: Boolean, saved: Boolean, downloaded: Boolean, positionMs: Long,
    canDownload: Boolean, onPlay: () -> Unit, onSave: () -> Unit, onRemove: () -> Unit, onDownload: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val tint = rememberArtworkTint(null, track.thumbnailUrl)
    val background = spokenCardColor(tint)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            SpokenCover(track.thumbnailUrl, if (podcast) Modifier.size(88.dp) else Modifier.width(78.dp).height(110.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                track.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(listOfNotNull(if (podcast) "Episode" else "Audiobook", track.durationMs?.let(PlaybackText::time), if (downloaded) "Downloaded" else "YouTube").joinToString(" · "), style = MaterialTheme.typography.labelSmall)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Options for ${track.title}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (saved) "Remove from library" else "Save to library") }, onClick = { menu = false; if (saved) onRemove() else onSave() })
                    if (canDownload && !downloaded) DropdownMenuItem(text = { Text("Download & play") }, onClick = { menu = false; onDownload() })
                }
            }
        }
        if (positionMs > 0) {
            Text("${positionMs / 60_000} min played", style = MaterialTheme.typography.bodySmall)
            track.durationMs?.takeIf { it > 0 }?.let { duration ->
                LinearProgressIndicator(progress = { (positionMs.toFloat() / duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = GroovioColors.Accent)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onPlay, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = GroovioColors.Background.copy(alpha = 0.6f), contentColor = GroovioColors.Text), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                Text(if (positionMs > 0) "Resume" else if (podcast) "Play episode" else "Listen", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = if (saved) onRemove else onSave,
                modifier = Modifier.background(GroovioColors.Background, CircleShape)) {
                Icon(if (saved) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                    if (saved) "Remove ${track.title} from library" else "Save ${track.title}", tint = GroovioColors.Accent, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
internal fun SpokenCover(url: String?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(GroovioColors.ArtworkFallback), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Default.Headphones, null, tint = GroovioColors.Accent)
    }
}

@Composable
private fun SpokenCollectionCard(album: OnlineAlbum, podcast: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GroovioColors.Surface).clickable(onClickLabel = "Open ${album.title}", onClick = onClick).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SpokenCover(album.thumbnailUrl, Modifier.size(80.dp))
        Column(Modifier.weight(1f)) {
            Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (podcast) "Show · Following" else "Book collection", color = GroovioColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Keep small white metadata readable even when a cover's dominant colour is very bright. */
internal fun spokenCardColor(tint: Color): Color {
    var result = lerp(tint, GroovioColors.Accent, 0.12f)
    repeat(16) {
        val contrast = (GroovioColors.Text.luminance() + 0.05f) / (result.luminance() + 0.05f)
        if (contrast >= 4.5f) return result
        result = lerp(result, GroovioColors.Background, 0.15f)
    }
    return GroovioColors.Surface
}
