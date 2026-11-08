package com.groovio.app.data

import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.Flow

data class SavedQueue(
    val items: List<MediaItem>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/** Everything Groovio stores on the phone. All functions are main-safe (Room uses its own threads). */
class LibraryRepository(
    private val dao: LibraryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val favourites: Flow<List<TrackEntity>> = dao.favourites()
    val playlists: Flow<List<PlaylistSummary>> = dao.playlists()

    fun recent(limit: Int = 50): Flow<List<TrackEntity>> = dao.recent(limit)
    fun isFavourite(mediaId: String): Flow<Boolean> = dao.isFavourite(mediaId)
    fun playlistName(id: Long): Flow<String?> = dao.playlistName(id)
    fun playlistItems(id: Long): Flow<List<PlaylistItem>> = dao.playlistItems(id)

    suspend fun setFavourite(track: TrackEntity, favourite: Boolean) {
        if (favourite) dao.addFavourite(track, clock()) else dao.removeFavourite(track.mediaId)
    }

    suspend fun recordPlay(track: TrackEntity) {
        dao.recordPlay(track, clock())
    }

    /** Keeps history bounded; called occasionally. */
    suspend fun trimHistory(keepDays: Int = 180) {
        dao.trimHistory(clock() - keepDays * 24L * 60 * 60 * 1000)
    }

    suspend fun createPlaylist(name: String): Long = dao.createPlaylist(name.trim(), clock())
    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name.trim())
    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)
    suspend fun addToPlaylist(id: Long, track: TrackEntity) = dao.addToPlaylist(id, track)
    suspend fun removeFromPlaylist(id: Long, position: Int) = dao.removeFromPlaylist(id, position)

    suspend fun saveQueue(items: List<MediaItem>, index: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        val tracks = items.mapNotNull(TrackMapping::fromMediaItem)
        // Only save when every item can be stored; otherwise indexes would shift.
        if (tracks.size != items.size) return
        dao.saveQueue(tracks, PlaybackStateEntity(0, index, positionMs.coerceAtLeast(0), shuffle, repeatMode))
    }

    suspend fun loadQueue(): SavedQueue? {
        val tracks = dao.queueTracks()
        if (tracks.isEmpty()) return null
        val state = dao.playbackState()
        return SavedQueue(
            items = tracks.map(TrackMapping::toMediaItem),
            index = (state?.index ?: 0).coerceIn(tracks.indices),
            positionMs = state?.positionMs ?: 0L,
            shuffle = state?.shuffle ?: false,
            repeatMode = state?.repeatMode ?: 0,
        )
    }
}
