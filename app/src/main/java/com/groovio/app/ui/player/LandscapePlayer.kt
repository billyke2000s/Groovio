package com.groovio.app.ui.player

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.awaitDispose
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.groovio.app.GroovioApplication
import com.groovio.app.online.OnlineTrack
import com.groovio.app.online.YouTubeMusic
import com.groovio.app.playback.PlayerUiState
import com.groovio.app.ui.theme.GroovioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.math.abs

/** Separate source from Groovio's audio session. Video is never allowed to own audio focus. */
internal data class LandscapeVideoSource(val url: String, val headers: Map<String, String>)

/** Conservative matching: a different edit or unrelated upload is worse than showing cover art. */
internal object LandscapeVideoMatch {
    private val words = Regex("[a-z0-9]+")
    private val unwanted = Regex("\\b(live|lyrics?|karaoke|cover|remix|reaction|visuali[sz]er|audio|instrumental|slowed|nightcore|extended|sped)\\b")
    private fun tokens(text: String) = words.findAll(text.lowercase()).map { it.value }.toList()

    fun accepts(title: String, artist: String, durationMs: Long, video: OnlineTrack): Boolean {
        if (durationMs !in 30_000L..900_000L) return false
        val name = video.title.lowercase()
        if (!name.contains("official") || !name.contains("video") || unwanted.containsMatchIn(name)) return false
        val trackWords = tokens(title.substringBefore('(').substringBefore('['))
            .filter { it.length > 1 && it !in setOf("feat", "ft", "featuring") }
        if (trackWords.isEmpty() || !trackWords.all { it in tokens(name) }) return false
        val artistWords = tokens(artist.substringBefore(',')).filter { it.length > 1 }
        val who = tokens(name + " " + video.artist.orEmpty())
        if (artistWords.isEmpty() || !artistWords.all { it in who }) return false
        val videoDuration = video.durationMs ?: return false
        // Only near-identical edits can be synchronised by seeking to the audio timestamp.
        return abs(durationMs - videoDuration) <= 4_000L
    }
}

/**
 * Performs network extraction only on the video UI's IO coroutine.
 * No audio service, queue, audio URL cache or MediaController call is changed.
 */
internal class LandscapeVideoResolver(private val youtube: YouTubeMusic) {
    fun find(title: String, artist: String, durationMs: Long): LandscapeVideoSource? {
        val matches = youtube.searchSpoken("$artist $title official music video")
            .filter { LandscapeVideoMatch.accepts(title, artist, durationMs, it) }
            .take(3)
        for (candidate in matches) {
            val source = runCatching { videoStream(candidate.id) }.getOrNull()
            if (source != null) return source
        }
        return null
    }

    private fun videoStream(videoId: String): LandscapeVideoSource? {
        val service = ServiceList.YouTube
        val info = StreamInfo.getInfo(service, service.streamLHFactory.fromId(videoId).url)
        val candidates = (info.videoOnlyStreams + info.videoStreams)
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotBlank() }
        fun height(resolution: String): Int = Regex("(\\d{3,4})p").find(resolution)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        // Prefer <=720p for mobile data and decoder load; accept higher only when necessary.
        val chosen = candidates.filter { height(it.resolution) in 240..720 }
            .maxByOrNull { height(it.resolution) }
            ?: candidates.filter { height(it.resolution) > 0 }.minByOrNull { height(it.resolution) }
            ?: return null
        val url = chosen.content
        val agent = when {
            YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
            YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
            else -> null
        }
        return LandscapeVideoSource(url, agent?.let { mapOf("User-Agent" to it) } ?: emptyMap())
    }
}

@Composable
internal fun LandscapePlayer(
    state: PlayerUiState,
    positionMs: Long,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSwipePrevious: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as GroovioApplication
    val videoMode by app.settings.landscapeVideoMode.collectAsStateWithLifecycle()
    val online by app.connectivity.online.collectAsStateWithLifecycle()
    val offline by app.settings.offlineOnly.collectAsStateWithLifecycle()
    val manager = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    fun onWifi(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.let {
        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } == true
    val wifi by produceState(initialValue = onWifi(), manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { value = onWifi() }
            override fun onLost(network: Network) { value = onWifi() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                value = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitDispose { manager.unregisterNetworkCallback(callback) }
    }
    var preferVideo by remember(state.currentMediaId) { mutableStateOf(true) }
    val canLookUp = preferVideo && online && !offline && state.currentMediaId?.startsWith("yt:") == true &&
        (videoMode == "always" || (videoMode == "wifi" && wifi))
    var found by remember(state.currentMediaId) { mutableStateOf<LandscapeVideoSource?>(null) }
    var resolving by remember(state.currentMediaId) { mutableStateOf(false) }
    var failedUrl by remember(state.currentMediaId) { mutableStateOf<String?>(null) }
    var firstFrame by remember(state.currentMediaId) { mutableStateOf(false) }
    var controlsVisible by remember(state.currentMediaId) { mutableStateOf(true) }
    val duration = state.durationMs ?: 0L

    LaunchedEffect(canLookUp, state.currentMediaId, state.title, state.artist, duration) {
        found = null
        resolving = false
        failedUrl = null
        val artist = state.artist?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (!canLookUp || duration <= 0L || state.title.isBlank()) return@LaunchedEffect
        resolving = true
        try {
            found = withContext(Dispatchers.IO) {
                runCatching { LandscapeVideoResolver(app.youtube).find(state.title, artist, duration) }.getOrNull()
            }
        } finally {
            resolving = false
        }
    }

    // A completely independent, silent renderer. Audio remains in GroovioPlaybackService.
    val video = if (canLookUp && found?.url != failedUrl) found else null
    val player = remember(video?.url) {
        video?.let { source ->
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)))
                .build().apply {
                    volume = 0f
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                    setMediaItem(MediaItem.fromUri(Uri.parse(source.url)))
                    prepare()
                }
        }
    }
    DisposableEffect(player) {
        firstFrame = false
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrame = true }
            override fun onPlayerError(error: PlaybackException) { failedUrl = video?.url }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }
    LaunchedEffect(player, state.isPlaying, positionMs) {
        player ?: return@LaunchedEffect
        val drift = abs(player.currentPosition - positionMs)
        if (drift > if (state.isPlaying) 1_200L else 400L) player.seekTo(positionMs)
        if (state.isPlaying) player.play() else player.pause()
    }

    val showingVideo = player != null && firstFrame && failedUrl != video?.url
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(3_500)
            controlsVisible = false
        }
    }
    var dragPosition by remember(state.currentMediaId) { mutableStateOf<Float?>(null) }
    Box(Modifier.fillMaxSize().background(GroovioColors.Background)) {
        if (player != null) {
            AndroidView(
                factory = { PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!showingVideo) {
            val artwork = rememberArtwork(state.artworkData, targetPx = 1024)
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val size = minOf(maxHeight - 28.dp, maxWidth * 0.45f).coerceAtLeast(48.dp)
                SwipeArtwork(state, artwork, onNext, onSwipePrevious, Modifier.size(size))
            }
        }

        // Tapping the picture/video reveals the controls without affecting music playback.
        Box(Modifier.fillMaxSize().clickable { controlsVisible = !controlsVisible })

        if (controlsVisible) Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, "Close player", tint = Color.White) }
            if (videoMode != "off" && !offline && state.currentMediaId?.startsWith("yt:") == true) {
                TextButton(onClick = { preferVideo = !preferVideo }) {
                    Text(if (preferVideo) "Artwork" else "Video", color = Color.White)
                }
            }
        }

        if (controlsVisible) Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                    Text(state.artist ?: "Unknown artist", fontSize = 12.sp, color = Color.LightGray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White)
                }
                IconButton(onClick = onTogglePlay, enabled = state.hasItem) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play", tint = Color.White)
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White)
                }
            }
            if (duration > 0L) {
                Slider(
                    value = dragPosition ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = { dragPosition = it },
                    onValueChangeFinished = {
                        dragPosition?.let { onSeek((it * duration).toLong()) }
                        dragPosition = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
