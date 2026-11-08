package com.groovio.app.ui.settings

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val app = context.applicationContext as GroovioApplication
    val settings = app.settings
    val dataSaver by settings.dataSaver.collectAsStateWithLifecycle()
    val autoplay by settings.autoplay.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmClearHistory by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, app.audioSessionId)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    val hasEqualizer = remember { eqIntent.resolveActivity(context.packageManager) != null }

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
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = hasEqualizer) { context.startActivity(eqIntent) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Column {
                        Text("Equalizer", color = if (hasEqualizer) GroovioColors.Text else GroovioColors.TextMuted)
                        Text(
                            if (hasEqualizer) "Opens your phone's equalizer" else "No equalizer on this phone",
                            color = GroovioColors.TextMuted, fontSize = 13.sp,
                        )
                    }
                }
            }

            item { Section("Playback") }
            item {
                Toggle("Autoplay", "When your music ends, keep playing similar songs", autoplay, settings::setAutoplay)
            }

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

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear listening history?") },
            text = { Text("Recently played and recommendations based on it are removed from this phone. Likes and playlists stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    scope.launch { app.library.clearHistory(); note = "Listening history cleared" }
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
