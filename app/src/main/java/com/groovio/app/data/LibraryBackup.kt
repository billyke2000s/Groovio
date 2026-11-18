package com.groovio.app.data

import org.json.JSONObject

data class LibraryBackup(val tracks: List<TrackEntity>, val likes: List<FavouriteEntity>,
    val playlists: List<PlaylistEntity>, val entries: List<PlaylistTrackEntity>) {
    companion object {
        /** Validate the entire file before any database writes. Paths/stream URLs are never imported. */
        fun parse(text: String): LibraryBackup {
            require(text.length <= 8 * 1024 * 1024) { "Backup is too large" }
            val j = JSONObject(text)
            require(j.getString("format") == "groovio" && j.getInt("version") == 1) { "Unsupported backup" }
            fun objects(key: String): List<JSONObject> {
                val a = j.getJSONArray(key)
                require(a.length() <= 50_000) { "Too many entries" }
                return (0 until a.length()).map { a.getJSONObject(it) }
            }
            val tracks = objects("tracks").map { t ->
                val id = t.getString("id")
                require(id.matches(Regex("yt:[A-Za-z0-9_-]+")) || id.startsWith("content://")) { "Invalid track" }
                val art = t.optString("art").takeIf { it.startsWith("https://") || it.startsWith("content://") }
                TrackEntity(id, t.getString("title").take(2000), t.optString("artist").ifBlank { null }?.take(2000), art)
            }
            require(tracks.map { it.mediaId }.distinct().size == tracks.size)
            val trackIds = tracks.map { it.mediaId }.toSet()
            val likes = objects("likes").map { FavouriteEntity(it.getString("id"), it.getLong("at")) }
            val playlists = objects("playlists").map { PlaylistEntity(it.getLong("id"), it.getString("name").take(200), it.getLong("at")) }
            val ids = playlists.map { it.id }.toSet()
            require(ids.size == playlists.size)
            val entries = objects("entries").map { PlaylistTrackEntity(it.getLong("playlist"), it.getInt("position"), it.getString("id")) }
            require(likes.all { it.mediaId in trackIds })
            require(entries.all { it.playlistId in ids && it.mediaId in trackIds && it.position >= 0 })
            require(entries.distinctBy { it.playlistId to it.position }.size == entries.size)
            return LibraryBackup(tracks, likes, playlists, entries)
        }
    }
}
