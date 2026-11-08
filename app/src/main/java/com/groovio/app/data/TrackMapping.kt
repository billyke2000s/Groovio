package com.groovio.app.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.groovio.app.online.OnlineMedia

/**
 * Converts between Media3 items and stored tracks. The playable URI is always derived
 * from the media id, so stored and externally-requested items can be played again.
 */
object TrackMapping {
    private const val YT = "yt:"

    /** "yt:<id>" -> groovio://youtube/<id>; a local item's media id is its content URI. */
    fun uriFor(mediaId: String): String =
        if (mediaId.startsWith(YT)) OnlineMedia.uriFor(mediaId.removePrefix(YT)) else mediaId

    fun isOnline(mediaId: String): Boolean = mediaId.startsWith(YT)

    fun fromMediaItem(item: MediaItem): TrackEntity? {
        if (item.mediaId.isBlank() || item.mediaId == MediaItem.DEFAULT_MEDIA_ID) return null
        val md = item.mediaMetadata
        return TrackEntity(
            mediaId = item.mediaId,
            title = md.title?.toString() ?: "Unknown track",
            artist = md.artist?.toString(),
            artworkUri = md.artworkUri?.toString(),
        )
    }

    fun toMediaItem(track: TrackEntity): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.mediaId)
            .setUri(uriFor(track.mediaId))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.artworkUri?.let(Uri::parse))
                    .build()
            )
            .build()

    /** Items from other controllers (Bluetooth, system resume) arrive without a URI. */
    fun withUri(item: MediaItem): MediaItem =
        if (item.localConfiguration != null || item.mediaId == MediaItem.DEFAULT_MEDIA_ID) item
        else item.buildUpon().setUri(uriFor(item.mediaId)).build()
}
