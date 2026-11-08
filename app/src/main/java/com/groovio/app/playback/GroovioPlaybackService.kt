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
    private val retried = mutableSetOf<String>()

    /** The item whose play has been recorded in history, reset on every transition. */
    private var recordedMediaId: String? = null
    private var saveJob: Job? = null

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

        player.addListener(object : Player.Listener {
            // Autoplay: when the queue runs out, continue with similar songs.
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && app.settings.autoplay.value) autoplay(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retried.clear()
                recordedMediaId = null
            }

            override fun onEvents(player: Player, events: Player.Events) {
                recordPlayIfStarted(player)
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
                val uri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                val id = OnlineMedia.youtubeId(uri) ?: return
                val httpError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                if (httpError && retried.add(id)) {
                    youtube.invalidate(id)
                    player.prepare()
                    player.play()
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
    }

    private var autoplayJob: Job? = null

    /** Appends YouTube's related songs for the last online song and keeps playing. */
    private fun autoplay(player: Player) {
        if (autoplayJob?.isActive == true || player.hasNextMediaItem()) return
        val lastId = OnlineMedia.youtubeId(player.currentMediaItem?.localConfiguration?.uri?.toString()) ?: return
        val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
        autoplayJob = scope.launch {
            val related = runCatching {
                withContext(Dispatchers.IO) { app.youtube.related(lastId) }
            }.getOrDefault(emptyList())
                .filter { "yt:${it.id}" !in queued }
                .take(10)
            if (related.isEmpty() || player.playbackState != Player.STATE_ENDED) return@launch
            player.addMediaItems(related.map(OnlineMedia::mediaItem))
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    /** Restores the last queue (paused) if nothing has been queued yet. */
    private fun restoreQueue(player: Player) {
        scope.launch {
            val saved = runCatching { library.loadQueue() }.getOrNull() ?: return@launch
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
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val shuffle = player.shuffleModeEnabled
        val repeat = player.repeatMode
        app.appScope.launch {
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
                val saved = runCatching { library.loadQueue() }.getOrNull()
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
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
