package com.groovio.app.spoken

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackMapping
import com.groovio.app.online.OnlineTrack
import com.groovio.app.ui.library.StoredTrackRow
import com.groovio.app.ui.theme.GroovioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var collections by remember(kind) { mutableStateOf<List<com.groovio.app.online.OnlineAlbum>>(emptyList()) }
    var opened by remember(kind) { mutableStateOf<SpokenCollection?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun search() {
        scope.launch {
            loading = true; message = null
            try {
                results = withContext(Dispatchers.IO) { app.youtube.searchSpoken(
                    query.ifBlank { if (kind == "Podcasts") "podcast full episode" else "public domain audiobook full book" }
                ) }
                collections = withContext(Dispatchers.IO) { runCatching { app.youtube.searchSpokenCollections(
                    query.ifBlank { if (kind == "Podcasts") "podcast episodes" else "public domain audiobook chapters" }
                ) }.getOrDefault(emptyList()) }
                if (results.isEmpty()) message = "No results. Try a show, author or book title."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = "Couldn't load YouTube results. Your saved content is still below." }
            finally { loading = false }
        }
    }
    LaunchedEffect(kind, offline) { if (!offline) search() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("$kind on YouTube", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
        item { Text("Public uploads available on YouTube. Availability and chapter listings depend on the uploader.", color = GroovioColors.TextMuted, modifier = Modifier.padding(horizontal = 16.dp)) }
        item { Row(Modifier.padding(16.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Show, episode, author or book") }, modifier = Modifier.weight(1f), singleLine = true)
            TextButton(onClick = { search() }, enabled = !offline && !loading) { Text("Search") }
        } }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
        message?.let { item { Text(it, modifier = Modifier.padding(16.dp)) } }
        followed.filter { it.kind == kind }.forEach { collection -> item(key = "followed:${collection.album.url}") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = { opened = collection }) { Text(collection.album.title) }
                TextButton(onClick = { app.spoken.unfollow(collection.album.url) }) { Text(if (kind == "Podcasts") "Unfollow show" else "Remove book collection") }
            }
        } }
        opened?.let { collection ->
            item { Text(if (kind == "Podcasts") "Episodes · ${collection.album.title}" else "Chapters · ${collection.album.title}", modifier = Modifier.padding(16.dp)) }
            item { TextButton(onClick = {
                collection.tracks.forEach { app.spoken.save(it, kind) }
                onDownload(collection.tracks, 0)
            }, enabled = downloadsEnabled && !offline && collection.tracks.isNotEmpty()) { Text("Download collection & play") } }
            itemsIndexed(collection.tracks, key = { i, t -> "episode:$i:${t.id}" }) { _, track ->
                StoredTrackRow(TrackMapping.fromOnline(track), false, {
                    app.spoken.save(track, kind); onPlay(listOf(track), 0)
                })
            }
        }
        if (!offline) collections.forEach { album -> item(key = "show:${album.url}") {
            Column(Modifier.padding(16.dp)) {
                Text(album.title)
                TextButton(onClick = { scope.launch {
                    loading = true
                    try {
                        val tracks = withContext(Dispatchers.IO) { app.youtube.albumTracks(album) }
                        app.spoken.follow(album, kind, tracks); opened = SpokenCollection(album, kind, tracks)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { message = "Couldn't load this collection. Try again." }
                    finally { loading = false }
                } }, enabled = !loading) { Text(if (kind == "Podcasts") "Follow & show episodes" else "Save & show chapters") }
            }
        } }
        val myItems = saved.filter { it.kind == kind }
        if (myItems.isNotEmpty()) item { Text(if (kind == "Podcasts") "Followed episodes" else "Saved books", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
        items(myItems, key = { "saved:${it.track.id}" }) { item ->
            Column {
                StoredTrackRow(TrackMapping.fromOnline(item.track), false, { onPlay(listOf(item.track), 0) })
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Text("${item.positionMs / 60_000} min played", modifier = Modifier.weight(1f), color = GroovioColors.TextMuted)
                    TextButton(onClick = { app.spoken.remove(item.track.id) }) { Text("Remove") }
                }
            }
        }
        if (!offline && results.isNotEmpty()) item { Text("Browse", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
        items(if (offline) emptyList() else results, key = { "result:${it.id}" }) { track ->
            Column {
                StoredTrackRow(TrackMapping.fromOnline(track), false, { app.spoken.save(track, kind); onPlay(listOf(track), 0) })
                Row(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = { app.spoken.save(track, kind) }) { Text(if (kind == "Podcasts") "Save episode" else "Save book") }
                    if (downloadsEnabled) TextButton(onClick = { app.spoken.save(track, kind); onDownload(listOf(track), 0) }) {
                        Text(if (downloads.any { it.mediaId == "yt:${track.id}" }) "Saved offline" else "Download & play")
                    }
                }
            }
        }
    }
}
