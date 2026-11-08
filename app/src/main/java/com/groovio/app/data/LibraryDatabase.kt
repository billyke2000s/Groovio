package com.groovio.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A song Groovio knows about. [mediaId] is "yt:<videoId>" or a local content:// URI. */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val mediaId: String,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
)

@Entity(
    tableName = "favourites",
    foreignKeys = [ForeignKey(TrackEntity::class, ["mediaId"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
)
data class FavouriteEntity(
    @PrimaryKey val mediaId: String,
    val addedAt: Long,
)

@Entity(
    tableName = "history",
    indices = [Index("mediaId"), Index("playedAt")],
    foreignKeys = [ForeignKey(TrackEntity::class, ["mediaId"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val playedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    indices = [Index("mediaId")],
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["mediaId"], ["mediaId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val position: Int,
    val mediaId: String,
)

/** The saved play queue, restored when the app or service restarts. */
@Entity(
    tableName = "queue",
    indices = [Index("mediaId")],
    foreignKeys = [ForeignKey(TrackEntity::class, ["mediaId"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
)
data class QueueEntity(
    @PrimaryKey val position: Int,
    val mediaId: String,
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

data class PlaylistSummary(val id: Long, val name: String, val trackCount: Int)

data class PlaylistItem(val position: Int, @Embedded val track: TrackEntity)

@Dao
abstract class LibraryDao {

    @Upsert abstract suspend fun upsertTrack(track: TrackEntity)
    @Upsert abstract suspend fun upsertTracks(tracks: List<TrackEntity>)

    // Favourites
    @Query(
        "SELECT t.* FROM tracks t JOIN favourites f ON f.mediaId = t.mediaId ORDER BY f.addedAt DESC"
    )
    abstract fun favourites(): Flow<List<TrackEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE mediaId = :mediaId)")
    abstract fun isFavourite(mediaId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertFavourite(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE mediaId = :mediaId")
    abstract suspend fun removeFavourite(mediaId: String)

    @Transaction
    open suspend fun addFavourite(track: TrackEntity, now: Long) {
        upsertTrack(track)
        insertFavourite(FavouriteEntity(track.mediaId, now))
    }

    // History
    @Insert protected abstract suspend fun insertHistory(entry: HistoryEntity)

    @Transaction
    open suspend fun recordPlay(track: TrackEntity, now: Long) {
        upsertTrack(track)
        insertHistory(HistoryEntity(mediaId = track.mediaId, playedAt = now))
    }

    /** Each track once, most recently played first. */
    @Query(
        """SELECT t.* FROM tracks t
           JOIN (SELECT mediaId, MAX(playedAt) AS last FROM history GROUP BY mediaId) h
             ON h.mediaId = t.mediaId
           ORDER BY h.last DESC LIMIT :limit"""
    )
    abstract fun recent(limit: Int): Flow<List<TrackEntity>>

    @Query("DELETE FROM history")
    abstract suspend fun clearHistory()

    @Query("DELETE FROM history WHERE playedAt < :before")
    abstract suspend fun trimHistory(before: Long)

    // Playlists
    @Insert protected abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    open suspend fun createPlaylist(name: String, now: Long): Long =
        insertPlaylist(PlaylistEntity(name = name, createdAt = now))

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylist(id: Long)

    @Query(
        """SELECT p.id, p.name, COUNT(pt.mediaId) AS trackCount FROM playlists p
           LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
           GROUP BY p.id ORDER BY p.createdAt DESC"""
    )
    abstract fun playlists(): Flow<List<PlaylistSummary>>

    @Query("SELECT name FROM playlists WHERE id = :id")
    abstract fun playlistName(id: Long): Flow<String?>

    @Query(
        """SELECT pt.position, t.* FROM playlist_tracks pt
           JOIN tracks t ON t.mediaId = pt.mediaId
           WHERE pt.playlistId = :id ORDER BY pt.position"""
    )
    abstract fun playlistItems(id: Long): Flow<List<PlaylistItem>>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_tracks WHERE playlistId = :id")
    protected abstract suspend fun nextPosition(id: Long): Int

    @Insert protected abstract suspend fun insertPlaylistTrack(entry: PlaylistTrackEntity)

    @Transaction
    open suspend fun addToPlaylist(playlistId: Long, track: TrackEntity) {
        upsertTrack(track)
        insertPlaylistTrack(PlaylistTrackEntity(playlistId, nextPosition(playlistId), track.mediaId))
    }

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id AND position = :position")
    abstract suspend fun removeFromPlaylist(id: Long, position: Int)

    // Saved queue
    @Query("DELETE FROM queue") protected abstract suspend fun clearQueue()
    @Insert protected abstract suspend fun insertQueue(entries: List<QueueEntity>)
    @Upsert protected abstract suspend fun upsertState(state: PlaybackStateEntity)

    @Transaction
    open suspend fun saveQueue(tracks: List<TrackEntity>, state: PlaybackStateEntity) {
        upsertTracks(tracks.distinctBy { it.mediaId })
        clearQueue()
        insertQueue(tracks.mapIndexed { i, t -> QueueEntity(i, t.mediaId) })
        upsertState(state)
    }

    @Query("SELECT t.* FROM queue q JOIN tracks t ON t.mediaId = q.mediaId ORDER BY q.position")
    abstract suspend fun queueTracks(): List<TrackEntity>

    @Query("SELECT * FROM playback_state WHERE id = 0")
    abstract suspend fun playbackState(): PlaybackStateEntity?
}

@Database(
    entities = [
        TrackEntity::class, FavouriteEntity::class, HistoryEntity::class, PlaylistEntity::class,
        PlaylistTrackEntity::class, QueueEntity::class, PlaybackStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun create(context: Context): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, "groovio.db").build()
    }
}
