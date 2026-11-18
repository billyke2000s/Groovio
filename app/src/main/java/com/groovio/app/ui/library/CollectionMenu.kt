package com.groovio.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.groovio.app.GroovioApplication
import com.groovio.app.data.PlaylistSummary
import com.groovio.app.data.RecentCollection
import com.groovio.app.data.TrackEntity
import com.groovio.app.ui.theme.GroovioColors

/**
 * Reuses the existing SongMenu's modal bottom sheet and MenuItem row design,
 * but actions apply to the whole collection. Downloading does not start playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionMenu(
    collection: RecentCollection,
    tracks: List<TrackEntity>,
    playlists: List<PlaylistSummary>,
    onPlay: (List<TrackEntity>) -> Unit,
    onDownload: (List<TrackEntity>) -> Unit,
    onOpenDownloads: () -> Unit,
    onCopyTo: (Long, List<TrackEntity>) -> Unit,
    onCopyToNew: (String, List<TrackEntity>) -> Unit,
    onRemoveFromSection: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as GroovioApplication
    val downloadsEnabled by app.settings.downloadsEnabled.collectAsStateWithLifecycle()
    val online by app.connectivity.online.collectAsStateWithLifecycle()
    val offlineOnly by app.settings.offlineOnly.collectAsStateWithLifecycle()
    val saved by app.downloads.tracks.collectAsStateWithLifecycle()
    val downloadable = tracks.filter { it.mediaId.startsWith("yt:") }.distinctBy { it.mediaId }
    // Avoid initialising WorkManager merely to render a menu with downloads disabled.
    val jobs = if (downloadsEnabled && downloadable.isNotEmpty())
        app.downloads.jobs.collectAsStateWithLifecycle(initialValue = emptyList()).value
    else emptyList()
    val savedIds = saved.map { it.mediaId }.toSet()
    val allSaved = downloadable.isNotEmpty() && downloadable.all { it.mediaId in savedIds }
    val pending = jobs.any { job ->
        !job.state.isFinished && downloadable.any { it.mediaId in job.tags }
    }
    var pickingPlaylist by rememberSaveable(collection.id) { mutableStateOf(false) }
    if (pickingPlaylist) {
        AddToPlaylistDialog(
            playlists = playlists.filterNot { it.id == collection.playlistId },
            onPick = { id -> onCopyTo(id, tracks); onDismiss() },
            onCreate = { name -> onCopyToNew(name, tracks); onDismiss() },
            onDismiss = onDismiss,
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GroovioColors.Surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(4.dp))
                        .background(GroovioColors.ArtworkFallback),
                    contentAlignment = Alignment.Center,
                ) {
                    val artwork = collection.artworkUri ?: tracks.firstOrNull()?.artworkUri
                    if (artwork != null) {
                        AsyncImage(
                            model = artwork, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    } else Icon(Icons.Default.QueueMusic, null, tint = GroovioColors.Accent)
                }
                Column {
                    Text(
                        collection.title, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${collection.kind} · ${tracks.size} songs",
                        color = GroovioColors.TextMuted, fontSize = 13.sp,
                    )
                }
            }
            HorizontalDivider(
                color = GroovioColors.SurfaceHigh,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (tracks.isNotEmpty()) {
                MenuItem(Icons.Default.PlayArrow, "Play playlist") {
                    onPlay(tracks); onDismiss()
                }
                if (downloadsEnabled && downloadable.isNotEmpty()) {
                    when {
                        allSaved -> MenuItem(Icons.Default.Check, "Downloaded · View downloads") {
                            onOpenDownloads(); onDismiss()
                        }
                        pending -> MenuItem(Icons.Default.Download, "Downloading · View downloads") {
                            onOpenDownloads(); onDismiss()
                        }
                        online && !offlineOnly -> MenuItem(Icons.Default.Download, "Download playlist") {
                            onDownload(downloadable); onDismiss()
                        }
                        else -> MenuItem(Icons.Default.Download, "View downloads · Offline") {
                            onOpenDownloads(); onDismiss()
                        }
                    }
                }
                MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to another playlist") {
                    pickingPlaylist = true
                }
            }
            onRemoveFromSection?.let { remove ->
                MenuItem(Icons.Default.FolderOff, "Remove from this section") {
                    remove(); onDismiss()
                }
            }
        }
    }
}
