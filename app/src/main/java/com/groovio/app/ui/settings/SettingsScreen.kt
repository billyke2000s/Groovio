package com.groovio.app.ui.settings

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.groovio.app.GroovioApplication
import com.groovio.app.ui.theme.GroovioColors
import kotlinx.coroutines.launch

/** Spotify-style settings. Every row does something real. */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    versionName: String,
    updateMessage: String?,
    onCheckUpdates: () -> Unit,
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    onSleepTimer: (Int?) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val app = context.applicationContext as GroovioApplication
    val settings = app.settings
    val equalizerEnabled by settings.equalizerEnabled.collectAsStateWithLifecycle()
    val equalizerBands by settings.equalizerBands.collectAsStateWithLifecycle()
    val effectStatus by app.effectStatus.collectAsStateWithLifecycle()
    val dataSaver by settings.dataSaver.collectAsStateWithLifecycle()
    val experimentalUpdates by settings.experimentalUpdates.collectAsStateWithLifecycle()
    val autoplay by settings.autoplay.collectAsStateWithLifecycle()
    val downloadsEnabled by settings.downloadsEnabled.collectAsStateWithLifecycle()
    val prepareNext by settings.prepareNext.collectAsStateWithLifecycle()
    val normalise by settings.normalise.collectAsStateWithLifecycle()
    val skipFailed by settings.skipFailed.collectAsStateWithLifecycle()
    val wifiOnly by settings.wifiOnly.collectAsStateWithLifecycle()
    val offlineOnly by settings.offlineOnly.collectAsStateWithLifecycle()
    val downloads by app.downloads.tracks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmClearHistory by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmDeleteDownloads by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val export = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = org.json.JSONObject(app.library.exportBackup()).put("settings", settings.export()).put("spoken", app.spoken.export()).put("spokenCollections", app.spoken.exportCollections()).put("collectionHistory", app.collectionHistory.export()).toString(2)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                        ?: error("Couldn't open backup")
                }
                note = "Backup saved"
            } catch (_: Exception) { note = "Couldn't save backup. Try another location." }
            finally { busy = false }
        }
    }
    val restore = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            require(buffer.size() + n <= 8 * 1024 * 1024) { "Backup too large" }
                            buffer.write(chunk, 0, n)
                        }
                        val bytes = buffer.toByteArray()
                        require(bytes.size <= 8 * 1024 * 1024) { "Backup too large" }
                        bytes.toString(Charsets.UTF_8)
                    } ?: error("Couldn't open backup")
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.groovio.app.data.LibraryBackup.parse(text) }
                pendingBackup = text
            } catch (_: Exception) { note = "Couldn't read this Groovio backup." }
            finally { busy = false }
        }
    }


    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Section("Audio quality") }
            item {
                Choice("Best available", "Highest quality stream, lossless when offered", !dataSaver) {
                    settings.setDataSaver(false); app.youtube.clearCache()
                }
            }
            item {
                Choice("Data saver", "Smallest stream; uses much less mobile data", dataSaver) {
                    settings.setDataSaver(true); app.youtube.clearCache()
                }
            }
            item { Section("Built-in equaliser") }
            item { Toggle("Equaliser", "Customisable five-band sound and presets", equalizerEnabled, settings::setEqualizerEnabled) }
            if (equalizerEnabled) {
                item { EqualizerPanel(equalizerBands, settings::setEqualizerBands) }
            }

            item { Section("Playback") }
            item {
                Toggle("Autoplay", "When your music ends, keep playing similar songs", autoplay, settings::setAutoplay)
            }

            item { Toggle("Prepare next song", "Resolve the next stream early; off in Data saver. Gapless depends on the source and device.", prepareNext, settings::setPrepareNext) }
            item { Toggle("Volume normalisation", "Gentle loudness reduction; quiet recordings aren't boosted. Requires supported Android 9+ audio effects.", normalise, settings::setNormalise) }
            effectStatus?.let { item { Text(it, modifier = Modifier.padding(20.dp)) } }
            item { Toggle("Skip unavailable tracks", "Skip at most three unavailable songs; network errors remain visible", skipFailed, settings::setSkipFailed) }

            item { Action("Sleep timer", "Stop playback after a while or at the end of a song") { showSleep = true } }

            item { Section("Downloads & offline") }
            item { Toggle("Enable downloads", "Show Download & play for songs and playlists", downloadsEnabled) {
                settings.setDownloadsEnabled(it)
                if (!it) app.downloads.cancelAll()
            } }
            item { Toggle("Download on Wi-Fi only", "Wait for an unmetered connection; changing this cancels pending downloads", wifiOnly) {
                settings.setWifiOnly(it); app.downloads.cancelAll()
            } }
            item { Toggle("Offline mode", "Play saved audio without contacting the online music source", offlineOnly) {
                settings.setOfflineOnly(it); if (it) app.downloads.cancelAll()
            } }
            item { Action("Downloads", "${downloads.size} songs · ${app.downloads.bytes() / (1024 * 1024)} MB saved", onOpenDownloads) }
            item { Action("Remove all downloads", "Clears offline cache; copies in Music/Groovio remain yours") { confirmDeleteDownloads = true } }

            item { Section("Your library") }
            item { Action("Back up library", if (busy) "Working…" else "Save playlists, likes and settings to a file") {
                if (!busy) export.launch("groovio-backup.json")
            } }
            item { Action("Restore library", "Merge a backup into your library; audio files aren't included") {
                if (!busy) restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            } }
            item { Section("Storage") }
            item {
                Action("Clear listening history", "Empties Recently played and Home recommendations") {
                    confirmClearHistory = true
                }
            }
            item {
                Action("Clear image cache", "Frees space used by album artwork") {
                    context.imageLoader.memoryCache?.clear()
                    context.imageLoader.diskCache?.clear()
                    note = "Image cache cleared"
                }
            }
            note?.let { item { Text(it, color = GroovioColors.Accent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp)) } }

            item { Section("Updates") }
            item { Toggle("Experimental updates", "Opt in to test builds. These may contain unfinished changes.", experimentalUpdates) {
                settings.setExperimentalUpdates(it); onCheckUpdates()
            } }
            item { Section("About") }
            item { Action("Groovio $versionName", updateMessage ?: "Check for updates", onCheckUpdates) }
            item {
                Action("Source code", "GPL-3.0 · github.com/billyke2000s/Groovio") {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/billyke2000s/Groovio"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            item {
                Text(
                    "Not affiliated with Spotify or YouTube.",
                    color = GroovioColors.TextMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }

    if (showSleep) AlertDialog(onDismissRequest = { showSleep = false }, title = { Text("Sleep timer") },
        text = { Column {
            listOf(null to "Off", 0 to "End of this song", 15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour").forEach { (minutes, label) ->
                TextButton(onClick = { onSleepTimer(minutes); showSleep = false; note = "Sleep timer: $label" }) { Text(label) }
            }
        } }, confirmButton = {})
    if (confirmDeleteDownloads) AlertDialog(onDismissRequest = { confirmDeleteDownloads = false },
        title = { Text("Remove all downloaded songs?") }, text = { Text("You can download them again when connected.") },
        confirmButton = { TextButton(onClick = {
            confirmDeleteDownloads = false; app.downloads.cancelAll()
            scope.launch { runCatching { app.downloads.clear() }.onSuccess { note = "Downloads removed" }.onFailure { note = "Couldn't remove every download" } }
        }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { confirmDeleteDownloads = false }) { Text("Cancel") } })
    pendingBackup?.let { text ->
        AlertDialog(onDismissRequest = { pendingBackup = null }, title = { Text("Restore this backup?") },
            text = { Text("Likes and playlists are added to your library. Settings are restored. Existing playlists stay; restoring twice creates copies. Local files may need to be picked again on a new phone.") },
            confirmButton = { TextButton(onClick = {
                pendingBackup = null
                scope.launch {
                    busy = true
                    try {
                        app.library.restoreBackup(text)
                        org.json.JSONObject(text).optJSONObject("settings")?.let(settings::restore)
                        org.json.JSONObject(text).optJSONArray("spoken")?.let(app.spoken::restore)
                        org.json.JSONObject(text).optJSONArray("spokenCollections")?.let(app.spoken::restoreCollections)
                        org.json.JSONObject(text).optJSONArray("collectionHistory")?.let(app.collectionHistory::restore)
                        app.youtube.clearCache(); app.downloads.cancelAll()
                        note = "Library restored"
                    } catch (_: Exception) { note = "Couldn't restore this backup" }
                    finally { busy = false }
                }
            }) { Text("Restore") } }, dismissButton = { TextButton(onClick = { pendingBackup = null }) { Text("Cancel") } })
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear listening history?") },
            text = { Text("Recently played and recommendations based on it are removed from this phone. Likes and playlists stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    scope.launch { app.collectionHistory.clear(); app.library.clearHistory(); note = "Listening history cleared" }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = GroovioColors.Accent))
        Column {
            Text(title)
            Text(subtitle, color = GroovioColors.TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, color = GroovioColors.TextMuted, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = GroovioColors.Accent))
    }
}

@Composable
private fun Action(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title)
        Text(subtitle, color = GroovioColors.TextMuted, fontSize = 13.sp)
    }
}

/**
 * Compact EQ with a real response curve and a searchable-by-scrolling preset menu.
 * Sliders update the same persisted five-band settings used by the audio session.
 */
@Composable
private fun EqualizerPanel(bands: List<Int>, onBandsChange: (List<Int>) -> Unit) {
    var presetsOpen by remember { mutableStateOf(false) }
    val frequencies = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .background(GroovioColors.Surface, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sound profile", fontWeight = FontWeight.SemiBold)
                Text("Choose a preset or adjust your own", color = GroovioColors.TextMuted, fontSize = 12.sp)
            }
            Box {
                OutlinedButton(onClick = { presetsOpen = true }) {
                    Text(com.groovio.app.playback.EqualizerPresets.selected(bands) + "  ▾")
                }
                DropdownMenu(expanded = presetsOpen, onDismissRequest = { presetsOpen = false }) {
                    com.groovio.app.playback.EqualizerPresets.all.forEach { preset ->
                        DropdownMenuItem(text = { Text(preset.name) }, onClick = {
                            onBandsChange(preset.bands)
                            presetsOpen = false
                        })
                    }
                }
            }
        }
        Text("+6 dB", color = GroovioColors.TextMuted, fontSize = 11.sp)
        Canvas(Modifier.fillMaxWidth().height(116.dp)) {
            val chartWidth = size.width
            val chartHeight = size.height
            val left = 12.dp.toPx()
            val right = chartWidth - left
            val baseline = chartHeight * 0.5f
            drawLine(GroovioColors.TextMuted.copy(alpha = 0.4f),
                Offset(left, baseline), Offset(right, baseline), 1.dp.toPx())
            val values = (0..4).map { i -> bands.getOrElse(i) { 0 }.coerceIn(-6, 6) }
            val points = values.mapIndexed { i, db ->
                Offset(left + (right - left) * i / 4f, chartHeight * (6 - db) / 12f)
            }
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, GroovioColors.Accent,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEach { drawCircle(GroovioColors.Accent, 4.dp.toPx(), it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("60", "230", "910", "3.6k", "14k").forEach { frequency ->
                Text(frequency, color = GroovioColors.TextMuted, fontSize = 11.sp)
            }
        }
        Text("Fine tune", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        frequencies.forEachIndexed { index, frequency ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(frequency, color = GroovioColors.TextMuted, fontSize = 13.sp)
                    val db = bands.getOrElse(index) { 0 }
                    Text("${if (db > 0) "+" else ""}$db dB", color = GroovioColors.Accent, fontSize = 13.sp)
                }
                androidx.compose.material3.Slider(
                    value = bands.getOrElse(index) { 0 }.toFloat(),
                    valueRange = -6f..6f, steps = 11,
                    onValueChange = { value ->
                        onBandsChange((0..4).map { i ->
                            if (i == index) kotlin.math.round(value).toInt() else bands.getOrElse(i) { 0 }
                        })
                    },
                    modifier = Modifier.semantics { contentDescription = "Equaliser $frequency" },
                )
            }
        }
        Text("Boosts use automatic headroom to avoid clipping. Effects may vary by audio output.",
            color = GroovioColors.TextMuted, fontSize = 12.sp)
    }
}
