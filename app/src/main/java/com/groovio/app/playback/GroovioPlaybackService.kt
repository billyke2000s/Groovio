package com.groovio.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.groovio.app.GroovioApplication
import com.groovio.app.MainActivity
import com.groovio.app.data.LibraryRepository
import com.groovio.app.data.TrackMapping
import com.groovio.app.online.OnlineMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class GroovioPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: GroovioApplication
    private val library: LibraryRepository get() = app.library

    /** Videos already retried once after an HTTP error, to avoid retry loops. */
    private val recovery = RecoveryPolicy()

    /** The item whose play has been recorded in history, reset on every transition. */
    private var recordedMediaId: String? = null
    private var saveJob: Job? = null
    private var nextPreparation: Job? = null
    private var preparedNextId: String? = null

    /** Speculation only fills the next URL's cache. It never touches the active item/player. */
    private fun prepareNext(player: Player) {
        if (!app.settings.prepareNext.value || app.settings.offlineOnly.value || app.settings.dataSaver.value ||
            !player.isPlaying || player.playbackState != Player.STATE_READY || !player.hasNextMediaItem()) return
        val next = player.getMediaItemAt(player.nextMediaItemIndex)
        val id = OnlineMedia.youtubeId(next.localConfiguration?.uri?.toString()) ?: return
        if (id == preparedNextId || app.downloads.localFile(next.mediaId) != null) return
        preparedNextId = id
        nextPreparation?.cancel()
        nextPreparation = scope.launch {
            try { kotlinx.coroutines.withTimeout(8000) { withContext(Dispatchers.IO) { app.youtube.resolve(id) } } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Playback resolves normally when the item is actually needed. */ }
        }
    }
    private val equalizer = SessionEffects()
    private val normalisation = SessionEffects(::createNormalisation)
    private fun updateEffects(sessionId: Int) {
        val eq = equalizer.apply(sessionId, app.settings.equalizerEnabled.value, app.settings.equalizerBands.value)
        val normal = normalisation.apply(sessionId, app.settings.normalise.value, emptyList())?.replace("Equaliser", "Normalisation")
        app.effectStatus.value = listOfNotNull(eq, normal).joinToString(" ").ifBlank { null }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        app = application as GroovioApplication
        val youtube = app.youtube

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        // groovio://youtube/<id> is turned into the real stream URL on the loader
        // thread (never the main thread) right before data is read.
        val dataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { spec ->
            val id = OnlineMedia.youtubeId(spec.uri.toString()) ?: return@Factory spec
            app.downloads.localFile("yt:$id")?.let { local ->
                return@Factory spec.withUri(Uri.fromFile(local))
            }
            if (app.settings.offlineOnly.value) throw IOException("Song is not downloaded")
            val audio = try {
                youtube.resolve(id)
            } catch (e: Exception) {
                throw IOException("Couldn't load audio for $id", e)
            }
            spec.buildUpon()
                .setUri(Uri.parse(audio.url))
                .setHttpRequestHeaders(spec.httpRequestHeaders + audio.headers)
                .build()
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            // true = request/abandon audio focus and duck/pause for calls and other apps.
            .setAudioAttributes(audioAttributes, true)
            // Pause when headphones are unplugged or Bluetooth audio disconnects.
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Let the system / equalizer apps attach to Groovio's audio (Settings → Equalizer).
        app.audioSessionId = player.audioSessionId
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        )

        scope.launch {
            kotlinx.coroutines.flow.combine(app.settings.equalizerEnabled, app.settings.equalizerBands, app.settings.normalise) { _, _, _ -> Unit }
                .collect { updateEffects(player.audioSessionId) }
        }
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                app.audioSessionId = audioSessionId
                updateEffects(audioSessionId)
            }

            // Autoplay: when the queue runs out, continue with similar songs.
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && app.settings.autoplay.value && !app.settings.offlineOnly.value) autoplay(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                nextPreparation?.cancel(); preparedNextId = null
                recovery.transition()
                player.setPlaybackSpeed(if (mediaItem?.mediaId?.let(app.spoken::contains) == true) app.settings.spokenSpeed.value else 1f)
                recordedMediaId = null
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                oldPosition.mediaItem?.mediaId?.takeIf(app.spoken::contains)?.let { id ->
                    app.spoken.progress(id, oldPosition.positionMs)
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (player.isPlaying) recovery.started()
                recordPlayIfStarted(player)
                prepareNext(player)
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                    )
                ) {
                    scheduleSave(player)
                }
            }

            // An expired or rejected stream URL: resolve it again once and resume.
            override fun onPlayerError(error: PlaybackException) {
                val item = player.currentMediaItem ?: return
                val uri = item.localConfiguration?.uri?.toString()
                val wantsPlay = player.playWhenReady

                // Download & Play starts online playback while WorkManager saves the file.
                // If this item later errors and a verified download exists, retry the local file
                // directly before surfacing an online-source error.
                val localFile = item?.mediaId
                    ?.takeIf { it.startsWith("yt:") }
                    ?.let(app.downloads::localFile)
                val localUri = localFile?.let(Uri::fromFile)
                if (localUri != null && uri != localUri.toString()) {
                    val index = player.currentMediaItemIndex
                    if (index != C.INDEX_UNSET) {
                        player.replaceMediaItem(index, item.buildUpon().setUri(localUri).build())
                        player.prepare()
                        player.playWhenReady = wantsPlay
                        return
                    }
                }

                val id = OnlineMedia.youtubeId(uri) ?: return
                val transient = error.errorCode in setOf(
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                )
                if (!app.settings.offlineOnly.value && recovery.retry(id, transient, wantsPlay)) {
                    youtube.invalidate(id)
                    player.prepare()
                    player.playWhenReady = wantsPlay
                } else {
                    val causes = generateSequence<Throwable>(error) { it.cause }.take(10).toList()
                    val blocked = causes.any { it is org.schabi.newpipe.extractor.exceptions.ReCaptchaException ||
                        it is org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException ||
                        it is com.groovio.app.online.InnertubeAudio.BlockedException }
                    val unavailable = !blocked && causes.any {
                        it is com.groovio.app.online.NoPlayableAudioException ||
                            it is org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
                    }
                    if (recovery.skip(unavailable, app.settings.skipFailed.value, wantsPlay, player.hasNextMediaItem())) {
                        app.playbackNotice.value = "Skipped unavailable track: ${player.mediaMetadata.title}"
                        player.seekToNextMediaItem(); player.prepare(); player.playWhenReady = wantsPlay
                    }
                }
            }
        })

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(SessionCallback())
            .build()

        restoreQueue(player)
        scope.launch { library.trimHistory() }
        scope.launch {
            while (true) {
                delay(5_000)
                if (player.isPlaying) saveNow(player)
            }
        }
        scope.launch {
            app.settings.offlineOnly.collect { offline ->
                if (offline && player.currentMediaItem?.mediaId?.startsWith("yt:") == true) {
                    val item = player.currentMediaItem ?: return@collect
                    val resume = player.playWhenReady
                    player.stop()
                    if (app.downloads.localFile(item.mediaId) != null) {
                        player.prepare(); player.playWhenReady = resume
                    } else {
                        player.pause()
                        app.playbackNotice.value = "Offline mode is on. Your saved music is waiting in Downloads."
                    }
                }
            }
        }
    }

    private var autoplayJob: Job? = null

    /** Appends YouTube's related songs for the last online song and keeps playing. */
    private fun autoplay(player: Player) {
        if (autoplayJob?.isActive == true || player.hasNextMediaItem() || player.currentMediaItem?.mediaId?.let(app.spoken::contains) == true) return
        val lastId = OnlineMedia.youtubeId(player.currentMediaItem?.localConfiguration?.uri?.toString()) ?: return
        val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
        autoplayJob = scope.launch {
            val related = runCatching {
                withContext(Dispatchers.IO) { app.youtube.related(lastId) }
            }.getOrDefault(emptyList())
                .filter { "yt:${it.id}" !in queued }
                .take(10)
            if (related.isEmpty() || player.playbackState != Player.STATE_ENDED || !player.playWhenReady ||
                player.currentMediaItem?.mediaId != "yt:$lastId" || app.settings.offlineOnly.value) return@launch
            player.addMediaItems(related.map(OnlineMedia::mediaItem))
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    private suspend fun loadQueueWithDownloads() = withContext(Dispatchers.IO) {
        val saved = library.loadQueue() ?: return@withContext null
        val downloads = app.downloads.tracks.value
        val byId = downloads.associateBy { it.mediaId }
        val bySong = downloads.filter { !it.artist.isNullOrBlank() }
            .groupBy { it.title to it.artist }
        saved.copy(items = saved.items.map { item ->
            if (!TrackMapping.isOnline(item.mediaId)) return@map item
            val metadata = item.mediaMetadata
            val download = byId[item.mediaId]
                ?: bySong[metadata.title?.toString().orEmpty() to metadata.artist?.toString()]?.singleOrNull()
                ?: return@map item
            // Keep the logical URI so removing the file later still permits online playback.
            TrackMapping.toMediaItem(download)
        })
    }

    /** Restores the last queue (paused) if nothing has been queued yet. */
    private fun restoreQueue(player: Player) {
        scope.launch {
            val saved = runCatching { loadQueueWithDownloads() }.getOrNull() ?: return@launch
            if (player.mediaItemCount > 0) return@launch
            player.setMediaItems(saved.items, saved.index, saved.positionMs)
            player.shuffleModeEnabled = saved.shuffle
            player.repeatMode = saved.repeatMode
            // Not prepared: nothing loads (or hits the network) until the user presses play.
        }
    }

    /** History counts a song once it actually starts playing. */
    private fun recordPlayIfStarted(player: Player) {
        if (!player.isPlaying) return
        val item = player.currentMediaItem ?: return
        if (item.mediaId == recordedMediaId) return
        val track = TrackMapping.fromMediaItem(item) ?: return
        recordedMediaId = item.mediaId
        if (app.spoken.contains(item.mediaId)) return
        app.appScope.launch { runCatching { library.recordPlay(track) } }
    }

    /** Saves queue and position at most once a second while things change. */
    private fun scheduleSave(player: Player) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1_000)
            saveNow(player)
        }
    }

    private fun saveNow(player: Player) {
        player.currentMediaItem?.mediaId?.takeIf(app.spoken::contains)?.let { id ->
            val complete = player.duration > 0 && player.duration - player.currentPosition < 5000
            app.spoken.progress(id, if (complete) 0 else player.currentPosition)
        }
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val shuffle = player.shuffleModeEnabled
        val repeat = player.repeatMode
        app.appScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            runCatching { library.saveQueue(items, index, position, shuffle, repeat) }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        // Bluetooth, car and other controllers send items without URIs; rebuild them.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(TrackMapping::withUri).toMutableList())

        // Android's "resume playback" card: offer the saved queue.
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaItemsWithStartPosition>()
            scope.launch {
                val saved = runCatching { loadQueueWithDownloads() }.getOrNull()
                if (saved == null) {
                    result.setException(UnsupportedOperationException("Nothing to resume"))
                } else {
                    result.set(MediaItemsWithStartPosition(saved.items, saved.index, saved.positionMs))
                }
            }
            return result
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        session?.player?.let { p ->
            sendBroadcast(
                Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, (p as? ExoPlayer)?.audioSessionId ?: 0)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            )
        }
        session?.run {
            saveNow(player)
            player.release()
            release()
        }
        equalizer.close()
        normalisation.close()
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
