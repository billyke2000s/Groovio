package com.groovio.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.groovio.app.data.PlaylistSummary
import com.groovio.app.data.TrackEntity
import com.groovio.app.playback.PlayerViewModel
import com.groovio.app.ui.theme.GroovioColors

/** Spotify-style song actions, opened from a song's ⋮ button or a long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenu(
    track: TrackEntity,
    vm: PlayerViewModel,
    playlists: List<PlaylistSummary>,
    onOpenArtist: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val liked by remember(track.mediaId) { vm.isFavourite(track.mediaId) }.collectAsStateWithLifecycle(false)
    var pickingPlaylist by rememberSaveable { mutableStateOf(false) }

    if (pickingPlaylist) {
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { vm.addToPlaylist(it, track) },
            onCreate = { vm.addToNewPlaylist(it, track) },
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
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(GroovioColors.ArtworkFallback),
                    contentAlignment = Alignment.Center) {
                    if (track.artworkUri != null) {
                        AsyncImage(track.artworkUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Default.MusicNote, null, tint = GroovioColors.Accent)
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    track.artist?.let { Text(it, color = GroovioColors.TextMuted, fontSize = 13.sp, maxLines = 1) }
                }
            }
            HorizontalDivider(color = GroovioColors.SurfaceHigh, modifier = Modifier.padding(vertical = 8.dp))
            MenuItem(
                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (liked) "Remove from Liked songs" else "Add to Liked songs",
                tint = if (liked) GroovioColors.Accent else GroovioColors.Text,
            ) { vm.setFavourite(track, !liked); onDismiss() }
            MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { pickingPlaylist = true }
            MenuItem(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next") { vm.playNext(track); onDismiss() }
            MenuItem(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") { vm.addToQueue(track); onDismiss() }
            track.artist?.let { artist ->
                MenuItem(Icons.Default.Person, "Go to artist") { onDismiss(); onOpenArtist(artist.substringBefore(',').trim()) }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = GroovioColors.Text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, fontSize = 16.sp)
    }
}
