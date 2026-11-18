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
fun SpokenScreen(kind: String, onPlay: (List<OnlineTrack>, Int) -> Unit, onDownload: (List<OnlineTrack>, Int) -> Unit) {
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
        if (offline) { loading = false; return@LaunchedEffect }
        if (query.isNotBlank()) delay(300)
        loading = true; message = null
        try {
            val response = withContext(Dispatchers.IO) {
                val tracks = async { app.youtube.searchSpoken(query.ifBlank { if (kind == "Podcasts") "podcast full episode" else "public domain audiobook full book" }) }
                val albums = async {
                    try { app.youtube.searchSpokenCollections(query.ifBlank { if (kind == "Podcasts") "podcast episodes" else "public domain audiobook chapters" }) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { emptyList() }
                }
                tracks.await() to albums.await()
            }
            results = response.first; collections = response.second
            if (results.isEmpty() && collections.isEmpty()) message = "Nothing found. Try another show, author or title."
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = "Couldn't refresh right now. Your saved listening is still here." }
        finally { loading = false }
    }
    fun play(track: OnlineTrack) {
        app.spoken.save(track, kind)
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
    } else SpokenDiscovery(kind, results, collections, followed, saved, downloads.map { it.mediaId }.toSet(), loading, offline, downloadsEnabled, message, query,
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
