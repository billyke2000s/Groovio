package com.groovio.app.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.groovio.app.data.RecentCollection
import com.groovio.app.data.TrackEntity
import com.groovio.app.ui.library.DownloadCollectionButton
import com.groovio.app.ui.library.StoredTrackRow
import com.groovio.app.ui.theme.GroovioColors

/** Opening a playlist browses its tracks; only explicit player actions alter the queue. */
@Composable
fun CollectionDetailScreen(
    collection: RecentCollection,
    tracks: List<TrackEntity>,
    currentMediaId: String?,
    onBack: () -> Unit,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onMore: (TrackEntity) -> Unit,
) {
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "collection-header") {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(GroovioColors.SurfaceHigh, GroovioColors.Background))
                )
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                CollectionArtwork(
                    collection,
                    Modifier.fillMaxWidth().padding(horizontal = 56.dp),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    collection.title, fontSize = 27.sp, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${collection.kind} · ${tracks.size} ${if (tracks.size == 1) "song" else "songs"}",
                    color = GroovioColors.TextMuted, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DownloadCollectionButton(tracks) { onPlay(tracks, 0) }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onPlay(tracks.shuffled(), 0) },
                        enabled = tracks.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle playlist")
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { onPlay(tracks, 0) },
                        enabled = tracks.isNotEmpty(),
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = GroovioColors.Accent,
                            contentColor = GroovioColors.OnAccent,
                            disabledContainerColor = GroovioColors.SurfaceHigh,
                        ),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play playlist",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (tracks.isEmpty()) {
            item {
                Text(
                    "This collection has no songs yet.",
                    color = GroovioColors.TextMuted,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        itemsIndexed(tracks, key = { index, track -> "$index:${track.mediaId}" }) { index, track ->
            StoredTrackRow(
                track = track,
                playing = track.mediaId == currentMediaId,
                onClick = { onPlay(tracks, index) },
                onLongClick = { onMore(track) },
                trailing = {
                    IconButton(onClick = { onMore(track) }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options for ${track.title}")
                    }
                },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Show a real four-cover collage for playlists with enough distinct artwork. */
@Composable
private fun CollectionArtwork(collection: RecentCollection, modifier: Modifier) {
    val covers = collection.tracks.mapNotNull { it.artworkUri }.distinct().take(4)
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
            .background(GroovioColors.ArtworkFallback),
        contentAlignment = Alignment.Center,
    ) {
        when {
            (collection.kind == "Playlist" || collection.kind == "Mix") && covers.size == 4 -> {
                Column(Modifier.fillMaxSize()) {
                    covers.chunked(2).forEach { row ->
                        Row(Modifier.weight(1f)) {
                            row.forEach { artwork ->
                                AsyncImage(
                                    model = artwork, contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
            collection.artworkUri != null || covers.isNotEmpty() ->
                AsyncImage(
                    model = collection.artworkUri ?: covers.first(),
                    contentDescription = "Artwork for ${collection.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            else -> Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null, tint = GroovioColors.Accent,
                modifier = Modifier.size(88.dp),
            )
        }
    }
}
