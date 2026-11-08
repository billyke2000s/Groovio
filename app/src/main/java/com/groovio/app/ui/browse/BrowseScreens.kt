package com.groovio.app.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import com.groovio.app.GroovioApplication
import com.groovio.app.online.ArtistPage
import com.groovio.app.online.OnlineAlbum
import com.groovio.app.online.OnlineTrack
import com.groovio.app.playback.PlaybackText
import com.groovio.app.ui.theme.GroovioColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** A detail page opened on top of a tab: an artist (by name) or an album. */
sealed interface Detail {
    data class Artist(val name: String) : Detail
    data class Album(val album: OnlineAlbum) : Detail
}

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** Loads [block] on the IO dispatcher; reloads when [key] or the retry counter change. */
@Composable
private fun <T> rememberLoad(key: Any, retry: Int, block: suspend () -> T): Load<T> {
    // remember + LaunchedEffect rather than produceState: lint misreads produceState here.
    var state by remember(key, retry) { mutableStateOf<Load<T>>(Load.Loading) }
    LaunchedEffect(key, retry) {
        state = try {
            Load.Ready(withContext(Dispatchers.IO) { block() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Load.Failed("Couldn't connect. Check your internet and try again.")
        } catch (e: Exception) {
            Load.Failed("Couldn't load this page. Try again.")
        }
    }
    return state
}

@Composable
fun ArtistScreen(
    name: String,
    currentMediaId: String?,
    onBack: () -> Unit,
    onPlay: (List<OnlineTrack>, Int) -> Unit,
    onOpenAlbum: (OnlineAlbum) -> Unit,
) {
    BackHandler(onBack = onBack)
    val youtube = (LocalContext.current.applicationContext as GroovioApplication).youtube
    var retry by remember { mutableIntStateOf(0) }
    val load = rememberLoad(name, retry) { youtube.artist(name) }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack)
        when (load) {
            Load.Loading -> Centered { CircularProgressIndicator(color = GroovioColors.Accent) }
            is Load.Failed -> Centered {
                Text(load.message, color = GroovioColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { retry++ }) { Text("Try again") }
            }
            is Load.Ready -> ArtistContent(load.value, currentMediaId, onPlay, onOpenAlbum)
        }
    }
}

@Composable
private fun ArtistContent(
    page: ArtistPage,
    currentMediaId: String?,
    onPlay: (List<OnlineTrack>, Int) -> Unit,
    onOpenAlbum: (OnlineAlbum) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    page.name, fontSize = 32.sp, fontWeight = FontWeight.Black,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (page.songs.isNotEmpty()) PlayButton { onPlay(page.songs, 0) }
            }
        }
        if (page.songs.isEmpty() && page.albums.isEmpty()) {
            item { Text("Nothing found for this artist.", color = GroovioColors.TextMuted, modifier = Modifier.padding(20.dp)) }
        }
        if (page.songs.isNotEmpty()) {
            item { SectionTitle("Popular") }
            itemsIndexed(page.songs, key = { _, t -> "s" + t.id }) { i, t ->
                SongRow(i + 1, t, currentMediaId == "yt:${t.id}") { onPlay(page.songs, i) }
            }
        }
        if (page.albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(page.albums, key = { it.url }) { a -> AlbumCard(a) { onOpenAlbum(a) } }
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(
    album: OnlineAlbum,
    currentMediaId: String?,
    onBack: () -> Unit,
    onPlay: (List<OnlineTrack>, Int) -> Unit,
    onShuffle: (List<OnlineTrack>) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val youtube = (LocalContext.current.applicationContext as GroovioApplication).youtube
    var retry by remember { mutableIntStateOf(0) }
    val load = rememberLoad(album.url, retry) { youtube.albumTracks(album) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { BackBar(onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = album.thumbnailUrl,
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(1f)
                        .background(GroovioColors.ArtworkFallback),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(album.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    album.artist?.let { artist ->
                        Text(
                            artist, color = GroovioColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp).clickable(onClickLabel = "Open artist") { onOpenArtist(artist) },
                        )
                    }
                    Text("Album", color = GroovioColors.TextMuted, fontSize = 13.sp)
                }
                val tracks = (load as? Load.Ready)?.value.orEmpty()
                IconButton(onClick = { onShuffle(tracks) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Default.Shuffle, "Shuffle album")
                }
                if (tracks.isNotEmpty()) PlayButton { onPlay(tracks, 0) }
            }
            Spacer(Modifier.height(8.dp))
        }
        when (load) {
            Load.Loading -> item { Centered { CircularProgressIndicator(color = GroovioColors.Accent) } }
            is Load.Failed -> item {
                Centered {
                    Text(load.message, color = GroovioColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { retry++ }) { Text("Try again") }
                }
            }
            is Load.Ready -> itemsIndexed(load.value, key = { i, t -> "$i${t.id}" }) { i, t ->
                SongRow(i + 1, t, currentMediaId == "yt:${t.id}", showArt = false) { onPlay(load.value, i) }
            }
        }
    }
}

@Composable
private fun BackBar(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = GroovioColors.Accent, contentColor = GroovioColors.OnAccent,
        ),
    ) { Icon(Icons.Default.PlayArrow, "Play", modifier = Modifier.size(32.dp)) }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun SongRow(number: Int, track: OnlineTrack, playing: Boolean, showArt: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Play ${track.title}", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$number", color = GroovioColors.TextMuted, fontSize = 14.sp, modifier = Modifier.width(22.dp))
        if (showArt) {
            AsyncImage(
                model = track.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(GroovioColors.ArtworkFallback),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (playing) GroovioColors.Accent else GroovioColors.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(track.artist, track.durationMs?.let(PlaybackText::time)).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = GroovioColors.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Square album card used in shelves. */
@Composable
fun AlbumCard(album: OnlineAlbum, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClickLabel = "Open ${album.title}", onClick = onClick)) {
        AsyncImage(
            model = album.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(6.dp)).background(GroovioColors.ArtworkFallback),
        )
        Text(album.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        album.artist?.let { Text(it, color = GroovioColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
