package com.groovio.app.playback

import android.app.PendingIntent
import android.content.Intent
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
import androidx.media3.session.MediaSessionService
import com.groovio.app.GroovioApplication
import com.groovio.app.MainActivity
import com.groovio.app.online.OnlineMedia
import java.io.IOException

class GroovioPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    /** Videos already retried once after an HTTP error, to avoid retry loops. */
    private val retried = mutableSetOf<String>()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val youtube = (application as GroovioApplication).youtube

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

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retried.clear()
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
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
