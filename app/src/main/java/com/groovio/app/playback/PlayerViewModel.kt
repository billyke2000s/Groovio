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
import com.groovio.app.GroovioApplication
import com.groovio.app.data.PlaylistSummary
import com.groovio.app.data.TrackEntity
import com.groovio.app.data.TrackMapping
import com.groovio.app.online.InnertubeAudio
import com.groovio.app.online.NoPlayableAudioException
import com.groovio.app.online.OnlineMedia
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import com.groovio.app.online.OnlineTrack
import com.groovio.app.online.SpotifyCollection
import com.groovio.app.online.SpotifyImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Connects the UI to [GroovioPlaybackService] through a Media3 [MediaController].
 * All controller calls happen on the main thread, as Media3 requires.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val library = (app as GroovioApplication).library

    private val _sleep = MutableStateFlow<SleepTimer?>(null)
    /** Active sleep timer, if any. */
    val sleepTimer: StateFlow<SleepTimer?> = _sleep.asStateFlow()
    private var sleepJob: Job? = null
    private val spotify: SpotifyImporter = (app as GroovioApplication).spotify

    private var importJob: Job? = null
    private val _import = MutableStateFlow<SpotifyImportProgress?>(null)
    /** Progress of matching a Spotify link's songs to YouTube, while it runs and after. */
    val spotifyImport: StateFlow<SpotifyImportProgress?> = _import.asStateFlow()

    /** Whether the current song is in Liked songs. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isFavourite: StateFlow<Boolean> = _state
        .map { it.currentMediaId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(false) else library.isFavourite(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val playlists: StateFlow<List<PlaylistSummary>> = library.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var controller: MediaController? = null
    private val future: ListenableFuture<MediaController>

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)

        // Sleep timer "end of track": pause as soon as the next song starts.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_sleep.value?.endOfTrack == true && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                controller?.pause()
                _sleep.value = null
            }
        }
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
            artworkUri = md.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 },
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            nextArtworkUri = player.nextMediaItemIndex
                .takeIf { it in 0 until count }
                ?.let { player.getMediaItemAt(it).mediaMetadata.artworkUri?.toString() },
            previousArtworkUri = player.previousMediaItemIndex
                .takeIf { it in 0 until count }
                ?.let { player.getMediaItemAt(it).mediaMetadata.artworkUri?.toString() },
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            audioLabel = AudioFormatLabel.selectedAudioFormat(player.currentTracks)
                ?.let(AudioFormatLabel::from),
            queue = queue,
            upcomingIndices = QueueOrder.upcoming(player.currentTimeline, player.currentMediaItemIndex, player.shuffleModeEnabled),
            currentIndex = if (count > 0) player.currentMediaItemIndex else -1,
            currentMediaId = player.currentMediaItem?.mediaId,
            error = player.playerError?.let(::describeError),
        )
    }

    private fun describeError(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.take(10)
            .flatMap { sequenceOf(it) + it.suppressed.asSequence() }.toList()
        return when {
            causes.any { it is SignInConfirmNotBotException || it is ReCaptchaException || it is InnertubeAudio.BlockedException } ->
                "YouTube is blocking this network right now. Try again later or switch network."
            causes.any { it is NoPlayableAudioException } -> "No playable audio for this song."
            causes.any { it.message?.contains("not downloaded") == true || it.message?.contains("not saved for offline") == true } ->
                "This song isn't saved for offline listening. Open Your music to play saved songs."
            else -> "Couldn't play this track."
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    /** null = off, 0 = end of this song, otherwise minutes. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        when {
            minutes == null -> _sleep.value = null
            minutes == 0 -> _sleep.value = SleepTimer(endsAtMs = null, endOfTrack = true)
            else -> {
                val ms = minutes * 60_000L
                _sleep.value = SleepTimer(endsAtMs = System.currentTimeMillis() + ms, endOfTrack = false)
                sleepJob = viewModelScope.launch {
                    delay(ms)
                    controller?.pause()
                    _sleep.value = null
                }
            }
        }
    }

    fun setSpeed(speed: Float) {
        getApplication<GroovioApplication>().settings.setSpokenSpeed(speed)
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }
    /** A rightward artwork swipe changes to the previous track, even if this song has played >3 seconds. */
    fun swipePrevious() { controller?.seekToPreviousMediaItem() }

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

    private fun currentTrack(): TrackEntity? =
        controller?.currentMediaItem?.let(TrackMapping::fromMediaItem)

    fun toggleFavourite() {
        val track = currentTrack() ?: return
        val now = !isFavourite.value
        viewModelScope.launch { library.setFavourite(track, now) }
    }

    fun addCurrentToPlaylist(playlistId: Long) {
        currentTrack()?.let { addToPlaylist(playlistId, it) }
    }

    fun addCurrentToNewPlaylist(name: String) {
        currentTrack()?.let { addToNewPlaylist(name, it) }
    }

    // Actions from the song menu (any song, not just the current one).

    fun isFavourite(mediaId: String): Flow<Boolean> = library.isFavourite(mediaId)

    fun setFavourite(track: TrackEntity, favourite: Boolean) {
        viewModelScope.launch { library.setFavourite(track, favourite) }
    }

    fun addToPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch { library.addToPlaylist(playlistId, track) }
    }

    fun addToNewPlaylist(name: String, track: TrackEntity) {
        if (name.isBlank()) return
        viewModelScope.launch { library.addToPlaylist(library.createPlaylist(name), track) }
    }

    /** Copies the whole collection without moving tracks or changing the original playlist. */
    fun addCollectionToPlaylist(playlistId: Long, tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            tracks.distinctBy { it.mediaId }.forEach { library.addToPlaylist(playlistId, it) }
        }
    }

    fun addCollectionToNewPlaylist(name: String, tracks: List<TrackEntity>) {
        if (name.isBlank() || tracks.isEmpty()) return
        viewModelScope.launch {
            val id = library.createPlaylist(name.trim())
            tracks.distinctBy { it.mediaId }.forEach { library.addToPlaylist(id, it) }
        }
    }

    /** Inserts right after the current song (or starts playing if the queue is empty). */
    fun playNext(track: TrackEntity) {
        val c = controller ?: return
        val item = TrackMapping.toMediaItem(track)
        if (c.mediaItemCount == 0) {
            c.setMediaItem(item); c.prepare(); c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, item)
        }
    }

    fun addToQueue(track: TrackEntity) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) playNext(track) else c.addMediaItem(TrackMapping.toMediaItem(track))
    }

    /** Reorders the queue (drag in the queue screen). */
    fun move(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) {
            c.moveMediaItem(from, to)
        }
    }

    /**
     * Plays a Spotify album/playlist/song: each song is matched to YouTube in order; the
     * first match starts playing straight away and later matches are appended to the queue.
     */
    fun playSpotify(collection: SpotifyCollection) = playSpotify(collection, false)
    fun playSpotify(collection: SpotifyCollection, download: Boolean) {
        importJob?.cancel()
        val historyTime = System.currentTimeMillis()
        val historyTracks = mutableListOf<TrackEntity>()
        fun rememberImport() {
            val snapshot = historyTracks.toList()
            if (snapshot.isNotEmpty() && collection.type != "track") rememberCollection(com.groovio.app.data.RecentCollection(
                "spotify:" + (collection.sourceUrl ?: collection.name), collection.name,
                if (collection.type == "album") "Album" else "Playlist",
                collection.artworkUrl ?: snapshot.firstOrNull()?.artworkUri, snapshot,
                source = collection.sourceUrl, playedAt = historyTime,
            ))
        }
        if (download) getApplication<GroovioApplication>().appScope.launch {
            runCatching { getApplication<GroovioApplication>().collectionImports.enqueue(collection) }
                .onFailure { getApplication<GroovioApplication>().playbackNotice.value = "Couldn't save collection. Try again." }
        }
        importJob = viewModelScope.launch {
            var started = false
            var found = 0
            var missed = 0
            val total = collection.tracks.size
            _import.value = SpotifyImportProgress(collection.name, 0, 0, total, done = false)
            for (track in collection.tracks) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val app = getApplication<GroovioApplication>()
                if (app.settings.offlineOnly.value) {
                    missed += total - found - missed
                    break
                }
                val match = withContext(Dispatchers.IO) { runCatching { spotify.match(track) }.getOrNull() }
                val c = controller ?: break
                if (match == null) {
                    missed++
                } else {
                    found++
                    historyTracks.add(TrackMapping.fromOnline(match.copy(title = track.title, artist = track.artist ?: match.artist)))
                    // Spotify's title/artist read better than YouTube's upload titles.
                    val item = OnlineMedia.mediaItem(match.copy(title = track.title, artist = track.artist ?: match.artist))
                    if (!started) {
                        c.setMediaItem(item)
                        c.prepare()
                        c.play()
                        started = true
                        rememberImport()
                    } else {
                        c.addMediaItem(item)
                    }
                }
                _import.value = SpotifyImportProgress(collection.name, found, missed, total, done = false)
            }
            rememberImport()
            _import.value = SpotifyImportProgress(collection.name, found, missed, total, done = true)
        }
    }

    /** Metadata-only history write; the existing playback path is unchanged. */
    fun rememberCollection(item: com.groovio.app.data.RecentCollection) {
        val app = getApplication<GroovioApplication>()
        app.appScope.launch(Dispatchers.IO) { app.collectionHistory.record(item) }
    }

    fun downloadTracks(tracks: List<TrackEntity>, play: Boolean = true) {
        val app = getApplication<Application>() as GroovioApplication
        app.downloads.enqueue(tracks)
        if (play) playStored(tracks, 0)
    }
    fun downloadOnline(tracks: List<OnlineTrack>, startIndex: Int = 0) {
        val app = getApplication<Application>() as GroovioApplication
        app.downloads.enqueue(tracks.map(TrackMapping::fromOnline))
        playTracks(tracks, startIndex)
    }

    private fun stopImport() {
        importJob?.cancel()
        importJob = null
        _import.value = null
    }

    /** Replaces the queue with stored tracks (liked songs, a playlist, history). */
    fun playStored(tracks: List<TrackEntity>, startIndex: Int) {
        val c = controller ?: return
        stopImport()
        if (tracks.isEmpty()) return
        c.setMediaItems(tracks.map(TrackMapping::toMediaItem), startIndex.coerceIn(tracks.indices), getApplication<GroovioApplication>().spoken.position(tracks[startIndex.coerceIn(tracks.indices)].mediaId))
        c.prepare()
        c.play()
    }

    /** Plays [tracks] in shuffle mode, starting from a random song. */
    fun shuffleTracks(tracks: List<OnlineTrack>) {
        if (tracks.isEmpty()) return
        playTracks(tracks, tracks.indices.random())
        controller?.shuffleModeEnabled = true
    }

    /** Replaces the queue with online tracks and starts at [startIndex]. */
    fun playTracks(tracks: List<OnlineTrack>, startIndex: Int) {
        val c = controller ?: return
        stopImport()
        if (tracks.isEmpty()) return
        c.setMediaItems(tracks.map(OnlineMedia::mediaItem), startIndex.coerceIn(tracks.indices), getApplication<GroovioApplication>().spoken.position("yt:${tracks[startIndex.coerceIn(tracks.indices)].id}"))
        c.prepare()
        c.play()
    }

    /** Replaces the queue with user-picked files and starts playing the first. */
    fun playFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        stopImport()
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

data class SpotifyImportProgress(
    val name: String,
    val found: Int,
    val missed: Int,
    val total: Int,
    val done: Boolean,
)

data class SleepTimer(val endsAtMs: Long?, val endOfTrack: Boolean)
