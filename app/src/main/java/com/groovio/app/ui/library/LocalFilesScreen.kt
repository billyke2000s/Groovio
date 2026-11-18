package com.groovio.app.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity
import com.groovio.app.offline.PublicMusicFolder
import com.groovio.app.ui.theme.GroovioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A single view of Groovio's verified downloads and imported phone audio. */
@Composable
fun LocalFilesScreen(
    currentMediaId: String?,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onMore: (TrackEntity) -> Unit,
    onPlayLegacy: (List<Uri>) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val app = context.applicationContext as GroovioApplication
    val scope = rememberCoroutineScope()
    val publicMusic = remember { app.downloads.publicMusic }
    val downloads by app.downloads.tracks.collectAsStateWithLifecycle()
    val jobs by app.downloads.jobs.collectAsStateWithLifecycle(initialValue = emptyList())
    var phoneFiles by remember { mutableStateOf(emptyList<TrackEntity>()) }
    var revision by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf("All") }
    var options by remember { mutableStateOf(false) }
    var activity by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TrackEntity?>(null) }
    var permissionGranted by remember { mutableStateOf(PublicMusicFolder.hasScanPermission(context)) }

    // Scanning MUST NOT attempt to re-export all downloads. Android's shared
    // Music folder is optional; private Groovio originals are authoritative.
    LaunchedEffect(revision) {
        scanning = true
        val result = withContext(Dispatchers.IO) { runCatching { publicMusic.tracks() } }
        result.onSuccess { phoneFiles = it }.onFailure {
            note = "Couldn't scan the phone folder. Check music access."
        }
        scanning = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionGranted = PublicMusicFolder.hasScanPermission(context)
        revision++
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        revision++
        if (!granted) note = "Allow music access to read songs added using a file manager."
    }
    val addMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            if (!PublicMusicFolder.available()) onPlayLegacy(uris)
            else scope.launch {
                importing = true
                val results = withContext(Dispatchers.IO) {
                    uris.map { runCatching { publicMusic.importAudio(it) } }
                }
                val ok = results.count { it.isSuccess }
                note = if (ok == uris.size) "Added $ok song(s)." else
                    "Added $ok song(s); ${uris.size - ok} couldn't be imported."
                importing = false
                revision++
            }
        }
    }
    val folderViewer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { revision++ }
    fun openFolder() {
        val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music/Groovio")
        val direct = Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val browse = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            }
        }
        runCatching {
            folderViewer.launch(if (direct.resolveActivity(context.packageManager) != null) direct else browse)
        }.onFailure { note = "Open Files → Internal storage → Music → Groovio." }
    }

    // Exclude the optional public copy of any still-downloaded Groovio track.
    val publishedUris = publicMusic.publishedCopyUris(downloads)
    val imported = phoneFiles.filterNot { it.mediaId in publishedUris }.distinctBy { it.mediaId }
    val everything = (downloads + imported).distinctBy { it.mediaId }
    val visible = when (selected) {
        "Downloads" -> downloads
        "On this phone" -> imported
        else -> everything
    }
    val active = jobs.filter { "audio-download" in it.tags && !it.state.isFinished }
    val failed = jobs.filter { job ->
        job.state == WorkInfo.State.FAILED && "audio-download" in job.tags &&
            !downloadJobAlreadySaved(job.tags, downloads.map { it.mediaId }.toSet(), downloads.map { it.title })
    }
    val mb = app.downloads.bytes() / (1024 * 1024)
    val unmatchedCollections = jobs.filter {
        "collection-import" in it.tags && it.state == WorkInfo.State.SUCCEEDED &&
            it.outputData.getInt("missed", 0) > 0
    }
    val unmatched = unmatchedCollections.sumOf { it.outputData.getInt("missed", 0) }
    val exampleMissing = unmatchedCollections.firstOrNull()
        ?.outputData?.getString("missingNames")?.takeIf { it.isNotBlank() }

    Column(Modifier.fillMaxSize().background(GroovioColors.Background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Your music", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { revision++ }, enabled = !scanning) {
                Icon(Icons.Default.Refresh, "Refresh music")
            }
            Box {
                IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreVert, "Music options") }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text("Add music") }, onClick = {
                        options = false; addMusic.launch(arrayOf("audio/*", "video/webm", "application/ogg", "application/octet-stream"))
                    })
                    if (PublicMusicFolder.available()) {
                        DropdownMenuItem(text = { Text("Browse Music/Groovio folder") }, onClick = {
                            options = false; openFolder()
                        })
                        DropdownMenuItem(text = { Text("Export downloads to phone folder") }, onClick = {
                            options = false
                            if (!exporting) scope.launch {
                                exporting = true
                                val result = withContext(Dispatchers.IO) { app.downloads.syncToMusicFolder() }
                                note = if (result.failed == 0) "${result.copied} songs available in Music/Groovio." else
                                    "${result.copied} available; ${result.failed} couldn't be copied. Saved Groovio originals are safe."
                                exporting = false
                                revision++
                            }
                        })
                    }
                    if (PublicMusicFolder.available() && !permissionGranted) {
                        DropdownMenuItem(text = { Text("Allow music access") }, onClick = {
                            options = false
                            PublicMusicFolder.permission()?.let { permissions.launch(it) }
                        })
                    }
                    DropdownMenuItem(text = { Text("Download activity") }, onClick = {
                        options = false; activity = true
                    })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { options = false; onSettings() })
                }
            }
        }
        Text("${downloads.size} downloaded · $mb MB saved offline", color = GroovioColors.TextMuted,
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlay(visible, 0) }, enabled = visible.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, null); Text("Play all")
            }
            TextButton(onClick = { onPlay(visible.shuffled(), 0) }, enabled = visible.isNotEmpty()) {
                Icon(Icons.Default.Shuffle, null); Text("Shuffle")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                addMusic.launch(arrayOf("audio/*", "video/webm", "application/ogg", "application/octet-stream"))
            }, enabled = !importing) { Text(if (importing) "Adding…" else "Add music") }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Downloads", "On this phone").forEach { option ->
                FilterChip(selected = selected == option, onClick = { selected = option },
                    label = { Text(option) }, shape = RoundedCornerShape(40.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = GroovioColors.SurfaceHigh,
                        selectedContainerColor = GroovioColors.Accent,
                        selectedLabelColor = GroovioColors.OnAccent))
            }
        }
        if (active.isNotEmpty() || failed.isNotEmpty()) {
            TextButton(onClick = { activity = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(if (failed.isNotEmpty()) "${failed.size} downloads need attention"
                else "${active.size} downloads in progress", color = GroovioColors.Accent)
            }
        }
        note?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = GroovioColors.TextMuted) }
        LazyColumn(Modifier.weight(1f)) {
            if (selected != "On this phone") {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Groovio downloads", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${downloads.size}", color = GroovioColors.TextMuted)
                    }
                }
                if (downloads.isEmpty()) item {
                    Text("Downloaded songs will be available here, even without internet.",
                        color = GroovioColors.TextMuted, modifier = Modifier.padding(20.dp))
                }
                itemsIndexed(downloads, key = { _, item -> "download:${item.mediaId}" }) { _, track ->
                    StoredTrackRow(track, playing = track.mediaId == currentMediaId,
                        onClick = { onPlay(visible, visible.indexOfFirst { it.mediaId == track.mediaId }) },
                        onLongClick = { onMore(track) }, trailing = {
                            IconButton(onClick = { deleting = track }) {
                                Icon(Icons.Default.DeleteOutline, "Remove downloaded ${track.title}")
                            }
                        })
                }
            }
            if (selected != "Downloads") {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("On this phone", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${imported.size}", color = GroovioColors.TextMuted)
                    }
                }
                if (imported.isEmpty()) item {
                    Text(if (scanning) "Scanning phone music…" else
                        "Use Add music to import MP3, FLAC and other audio files.",
                        color = GroovioColors.TextMuted, modifier = Modifier.padding(20.dp))
                }
                itemsIndexed(imported, key = { _, item -> "phone:${item.mediaId}" }) { _, track ->
                    StoredTrackRow(track, playing = track.mediaId == currentMediaId,
                        onClick = { onPlay(visible, visible.indexOfFirst { it.mediaId == track.mediaId }) },
                        onLongClick = { onMore(track) })
                }
            }
        }
    }
    deleting?.let { track ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("Remove download?") },
            text = { Text("Remove the offline Groovio copy of ${track.title}? Playlists and separately exported files will stay.") },
            confirmButton = { TextButton(onClick = {
                deleting = null
                scope.launch {
                    runCatching { app.downloads.remove(track) }
                        .onFailure { note = "Couldn't remove the saved download." }
                }
            }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    if (activity) AlertDialog(onDismissRequest = { activity = false },
        title = { Text("Download activity") },
        text = { Text(buildString {
            append("${downloads.size} playable songs · $mb MB\n")
            append("${active.size} pending downloads\n")
            append("${failed.size} unresolved downloads")
            if (unmatched > 0) {
                append("\n$unmatched unavailable from playlist matching")
                exampleMissing?.let { append("\nCouldn't match: $it") }
            }
            append("\n\nA problem making an extra copy in Music/Groovio does not mean the original download failed.")
        }) },
        confirmButton = { TextButton(onClick = { activity = false }) { Text("Done") } })
}

/** Ignore historic export failures once the verified private audio exists. */
internal fun downloadJobAlreadySaved(tags: Set<String>, ids: Set<String>, titles: List<String>): Boolean {
    if (tags.any { it.startsWith("yt:") && it in ids }) return true
    val title = tags.firstOrNull { it.startsWith("title:") }?.removePrefix("title:") ?: return false
    return titles.any { it.equals(title, ignoreCase = true) }
}
