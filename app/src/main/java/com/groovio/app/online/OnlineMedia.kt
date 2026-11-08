package com.groovio.app.online

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Online tracks are queued with a "groovio://youtube/<id>" URI. The real stream URL is
 * resolved only when playback starts, because YouTube URLs expire after a few hours.
 */
object OnlineMedia {
    private const val YOUTUBE_PREFIX = "groovio://youtube/"

    fun uriFor(videoId: String): String = YOUTUBE_PREFIX + videoId

    /** The video id for a Groovio YouTube URI, or null for anything else. */
    fun youtubeId(uri: String?): String? =
        uri?.takeIf { it.startsWith(YOUTUBE_PREFIX) }
            ?.removePrefix(YOUTUBE_PREFIX)
            ?.takeIf { it.isNotBlank() && it.none { c -> c == '/' || c == '?' } }

    fun mediaItem(track: OnlineTrack): MediaItem =
        MediaItem.Builder()
            .setMediaId("yt:${track.id}")
            .setUri(uriFor(track.id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnailUrl?.let(Uri::parse))
                    .build()
            )
            .build()
}
