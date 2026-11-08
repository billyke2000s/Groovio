package com.groovio.app.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Connects the UI to [GroovioPlaybackService] through a Media3 [MediaController].
 * All controller calls happen on the main thread, as Media3 requires.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val future: ListenableFuture<MediaController>

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)
    }

    init {
        val token = SessionToken(app, ComponentName(app, GroovioPlaybackService::class.java))
        future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = try {
                future.get()
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "Player unavailable")
                return@addListener
            }
            controller = c
            c.addListener(listener)
            refresh(c)
        }, ContextCompat.getMainExecutor(app))
    }

    /**
     * Current position, polled twice a second. Collect it with a lifecycle-aware
     * collector so polling stops while the UI is not visible.
     */
    val positionMs: Flow<Long> = flow {
        while (true) {
            emit(controller?.currentPosition ?: 0L)
            delay(500)
        }
    }

    private fun refresh(player: Player) {
        val md = player.mediaMetadata
        val count = player.mediaItemCount
        val queue = (0 until count).map { i ->
            val item = player.getMediaItemAt(i).mediaMetadata
            QueueEntry(i, item.title?.toString() ?: "Unknown track", item.artist?.toString())
        }
        _state.value = PlayerUiState(
            connected = true,
            hasItem = count > 0,
            title = md.title?.toString() ?: "Unknown track",
            artist = (md.artist ?: md.albumArtist)?.toString(),
            artworkData = md.artworkData,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 },
            hasNext = player.hasNextMediaItem(),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            audioLabel = AudioFormatLabel.selectedAudioFormat(player.currentTracks)
                ?.let(AudioFormatLabel::from),
            queue = queue,
            currentIndex = if (count > 0) player.currentMediaItemIndex else -1,
            error = player.playerError?.let { "Couldn't play this track" },
        )
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let { it.repeatMode = PlaybackText.nextRepeatMode(it.repeatMode) }
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(index)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    /** Replaces the queue with user-picked files and starts playing the first. */
    fun playFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { uris.map(::toMediaItem) }
            val c = controller ?: return@launch
            c.setMediaItems(items)
            c.prepare()
            c.play()
        }
    }

    private fun toMediaItem(uri: Uri): MediaItem {
        val resolver = getApplication<Application>().contentResolver
        // Not every document provider offers persistable grants; the temporary
        // grant still allows playback during this session.
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        val name = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (_: Exception) {
            null
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name?.substringBeforeLast('.') ?: "Local track")
                    .build()
            )
            .build()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller = null
        MediaController.releaseFuture(future)
    }
}
