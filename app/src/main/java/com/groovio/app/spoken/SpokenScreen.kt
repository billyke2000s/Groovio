package com.groovio.app.spoken

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackMapping
import com.groovio.app.online.OnlineAlbum
import com.groovio.app.online.OnlineTrack
import com.groovio.app.ui.library.StoredTrackRow
import com.groovio.app.ui.theme.GroovioColors
import kotlinx.coroutines.*

@Composable
fun SpokenScreen(kind: String, onPlay: (List<OnlineTrack>, Int) -> Unit, onDownload: (List<OnlineTrack>, Int) -> Unit, playbackError: String? = null) {
    val app = LocalContext.current.applicationContext as GroovioApplication
    val followed by app.spoken.followed.collectAsStateWithLifecycle()
    val saved by app.spoken.saved.collectAsStateWithLifecycle()
    val offline by app.settings.offlineOnly.collectAsStateWithLifecycle()
    val downloads by app.downloads.tracks.collectAsStateWithLifecycle()
    val downloadsEnabled by app.settings.downloadsEnabled.collectAsStateWithLifecycle()
    var query by remember(kind) { mutableStateOf("") }
    var results by remember(kind) { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var collections by remember(kind) { mutableStateOf<List<OnlineAlbum>>(emptyList()) }
    var opened by remember(kind) { mutableStateOf<SpokenCollection?>(null) }
    var loading by remember(kind) { mutableStateOf(false) }
    var message by remember(kind) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(kind, offline, query) {
        if (offline) {
            loading = false
            results = emptyList()
            collections = emptyList()
            return@LaunchedEffect
        }
        if (query.isNotBlank()) delay(300)
        loading = true
        message = null
        results = emptyList()
        collections = emptyList()
        // Publish recordings first. Collections are optional and load afterwards:
        // avoid two simultaneous YouTube searches on the same phone/network.
        val episodeQuery = query.ifBlank { if (kind == "Podcasts") "podcast full episode" else "public domain audiobook full book" }
        val collectionsQuery = query.ifBlank { if (kind == "Podcasts") "podcast episodes" else "public domain audiobook chapters" }
        try {
            val tracks = withTimeoutOrNull(20_000) {
                runInterruptible(Dispatchers.IO) { app.youtube.searchSpoken(episodeQuery) }
            }
            if (tracks == null) message = "Episode search timed out. Try again or search by title."
            results = tracks.orEmpty()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            message = "Couldn't refresh recordings. Your saved listening is still here."
        } finally { loading = false }
        try {
            collections = withTimeoutOrNull(20_000) {
                runInterruptible(Dispatchers.IO) { app.youtube.searchSpokenCollections(collectionsQuery) }
            }.orEmpty()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Collections are optional and never gate recordings. */ }
        if (results.isEmpty() && collections.isEmpty() && message == null) {
            message = "Nothing found. Try another show, author or title."
        }
    }
    fun play(track: OnlineTrack) {
        app.spoken.remember(track, kind)
        val collection = opened
        if (collection != null) {
            val entry = com.groovio.app.data.RecentCollection(collection.album.url, collection.album.title,
                if (kind == "Podcasts") "Show" else "Audiobook", collection.album.thumbnailUrl,
                collection.tracks.map(TrackMapping::fromOnline), source = collection.album.url)
            app.appScope.launch(Dispatchers.IO) { app.collectionHistory.record(entry) }
        } else if (kind == "Audiobooks") {
            val entry = com.groovio.app.data.RecentCollection("book:${track.id}", track.title, "Audiobook", track.thumbnailUrl, listOf(TrackMapping.fromOnline(track)))
            app.appScope.launch(Dispatchers.IO) { app.collectionHistory.record(entry) }
        }
        onPlay(listOf(track), 0)
    }
    fun open(album: OnlineAlbum) {
        scope.launch {
            loading = true; message = null
            try { opened = SpokenCollection(album, kind, withContext(Dispatchers.IO) { app.youtube.albumTracks(album) }) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "Couldn't open this collection. Try again." }
            finally { loading = false }
        }
    }
    val detail = opened
    if (detail != null) {
        BackHandler { opened = null }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { opened = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to $kind") }
                Text(if (kind == "Podcasts") "Episodes" else "Chapters", style = MaterialTheme.typography.titleLarge)
            } }
            if (playbackError != null) item {
                Text("Playback: $playbackError", color = GroovioColors.Accent,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SpokenCover(detail.album.thumbnailUrl, Modifier.size(164.dp))
                Text(detail.album.title, style = MaterialTheme.typography.headlineSmall)
                detail.album.artist?.let { Text(it, color = GroovioColors.TextMuted) }
                Text("${detail.tracks.size} ${if (kind == "Podcasts") "episodes" else "recordings"}", color = GroovioColors.TextMuted)
                val isFollowed = followed.any { it.album.url == detail.album.url }
                OutlinedButton(onClick = {
                    if (isFollowed) app.spoken.unfollow(detail.album.url) else app.spoken.follow(detail.album, kind, detail.tracks)
                }) { Text(if (isFollowed) if (kind == "Podcasts") "Following" else "Saved to library" else if (kind == "Podcasts") "Follow show" else "Save book collection") }
                if (downloadsEnabled && !offline && detail.tracks.isNotEmpty()) Button(onClick = {
                    app.spoken.follow(detail.album, kind, detail.tracks)
                    app.spoken.saveAll(detail.tracks, kind)
                    val entry = com.groovio.app.data.RecentCollection(detail.album.url, detail.album.title, if (kind == "Podcasts") "Show" else "Audiobook",
                        detail.album.thumbnailUrl, detail.tracks.map(TrackMapping::fromOnline), source = detail.album.url)
                    app.appScope.launch(Dispatchers.IO) { app.collectionHistory.record(entry) }
                    onDownload(detail.tracks, 0)
                }) { Text("Download collection & play") }
            } }
            itemsIndexed(detail.tracks, key = { i, track -> "$i:${track.id}" }) { _, track ->
                StoredTrackRow(TrackMapping.fromOnline(track), false, { play(track) })
            }
        }
    } else SpokenDiscovery(kind, results, collections, followed, saved, downloads.map { it.mediaId }.toSet(), loading, offline, downloadsEnabled,
        playbackError?.let { "Playback: $it" } ?: message, query,
        onQuery = { query = it }, onPlay = ::play, onSave = { app.spoken.save(it, kind) }, onRemove = { app.spoken.remove(it.id) },
        onDownload = { track ->
            app.spoken.save(track, kind)
            if (kind == "Audiobooks") {
                val entry = com.groovio.app.data.RecentCollection("book:${track.id}", track.title, "Audiobook", track.thumbnailUrl, listOf(TrackMapping.fromOnline(track)))
                app.appScope.launch(Dispatchers.IO) { app.collectionHistory.record(entry) }
            }
            onDownload(listOf(track), 0)
        }, onOpen = ::open, onOpenSaved = { opened = it })
}
