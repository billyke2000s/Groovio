package com.groovio.app.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.groovio.app.GroovioApplication
import com.groovio.app.data.TrackEntity
import com.groovio.app.offline.PublicMusicFolder
import com.groovio.app.ui.theme.GroovioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A friendly, dedicated view of Music/Groovio. Imported phone MP3s live beside
 * separately published online downloads, and are played by the existing Media3 queue.
 */
@Composable
fun LocalFilesScreen(
    currentMediaId: String?,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onPlayLegacy: (List<Uri>) -> Unit,
    onBack: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val app = context.applicationContext as GroovioApplication
    val scope = rememberCoroutineScope()
    val publicMusic = remember { app.downloads.publicMusic }
    var files by remember { mutableStateOf(emptyList<TrackEntity>()) }
    var refreshing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(PublicMusicFolder.hasScanPermission(context)) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(revision) {
        refreshing = true
        val (syncResult, listResult) = withContext(Dispatchers.IO) {
            val sync = runCatching { app.downloads.syncToMusicFolder() }
            sync to runCatching { publicMusic.tracks() }
        }
        listResult.onSuccess { files = it }.onFailure {
            note = "Couldn't read Music/Groovio. Allow music access, then refresh."
        }
        syncResult.onFailure { note = "Some older downloads couldn't be copied to the shared folder. You can retry with Refresh." }
        refreshing = false
    }
    // Re-read after Files app edits a folder while Groovio was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionGranted = PublicMusicFolder.hasScanPermission(context)
        revision++
    }

    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        revision++
        note = if (granted) null else "Without music access, Groovio may only see files it created itself."
    }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (!PublicMusicFolder.available()) {
            onPlayLegacy(uris)
        } else {
            scope.launch {
                importing = true
                val results = withContext(Dispatchers.IO) {
                    uris.map { uri -> runCatching { publicMusic.importAudio(uri) } }
                }
                val copied = results.count { it.isSuccess }
                val failed = results.size - copied
                note = when {
                    failed == 0 -> "Added $copied audio file(s) to Music/Groovio."
                    else -> "Added $copied file(s); $failed couldn't be imported. Only supported audio formats are accepted."
                }
                importing = false
                revision++
            }
        }
    }
    val folderViewer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        revision++
    }

    fun openFolder() {
        val folderUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Music/Groovio")
        val direct = Intent(Intent.ACTION_VIEW)
            .setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val browse = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
            }
        }
        // Some Files apps support opening the exact folder; Android's folder picker
        // is the safe fallback when the device's Files app doesn't support that intent.
        runCatching {
            folderViewer.launch(if (direct.resolveActivity(context.packageManager) != null) direct else browse)
        }.onFailure {
            note = "Open Files → Internal storage → Music → Groovio to copy music."
        }
    }

    Column(Modifier.fillMaxSize().background(GroovioColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("Local Files", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { revision++ }, enabled = !refreshing) {
                Icon(Icons.Default.Refresh, "Refresh music folder")
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, "File options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Manage downloads") }, onClick = {
                        menuExpanded = false; onDownloads()
                    })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = {
                        menuExpanded = false; onSettings()
                    })
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .background(GroovioColors.Surface, RoundedCornerShape(14.dp))
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FolderOpen, null, tint = GroovioColors.Accent)
                Text("Your music folder", fontWeight = FontWeight.Bold)
            }
            Text("Internal storage → Music → Groovio", color = GroovioColors.Accent)
            Text(
                if (PublicMusicFolder.available())
                    "Copy MP3, FLAC, M4A and other supported audio here using your Files app. Groovio downloads are copied here automatically."
                else "On Android 8–9, choose music directly from your device. Public-folder copying requires Android 10+.",
                color = GroovioColors.TextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickAudio.launch(arrayOf("audio/*", "video/webm", "application/ogg", "application/octet-stream")) },
                    enabled = !importing) {
                    Text(if (importing) "Adding…" else if (PublicMusicFolder.available()) "Add music" else "Choose audio")
                }
                OutlinedButton(onClick = ::openFolder) { Text("Browse folder") }
            }
            if (PublicMusicFolder.available() && !permissionGranted) {
                Text("Give Groovio music access to find MP3s copied using another file manager.",
                    color = GroovioColors.TextMuted)
                TextButton(onClick = {
                    PublicMusicFolder.permission()?.let { permissionRequest.launch(it) }
                }) { Text("Allow music access") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("On this phone", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("${files.size} tracks", color = GroovioColors.TextMuted)
        }
        if (note != null) Text(note.orEmpty(), modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
            color = GroovioColors.Accent)
        if (refreshing) Text("Scanning your music…", color = GroovioColors.TextMuted,
            modifier = Modifier.padding(horizontal = 20.dp))
        if (!refreshing && files.isEmpty()) {
            Text("No music here yet. Tap Add music to copy some MP3s, or save a song in Groovio. Your files will appear below.",
                modifier = Modifier.padding(20.dp), color = GroovioColors.TextMuted)
        }
        if (files.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tap any song to play it", color = GroovioColors.TextMuted)
                TextButton(onClick = { onPlay(files, 0) }) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("Play all")
                }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(files, key = { _, track -> track.mediaId }) { index, track ->
                StoredTrackRow(track, playing = track.mediaId == currentMediaId,
                    onClick = { onPlay(files, index) })
            }
        }
    }
}
