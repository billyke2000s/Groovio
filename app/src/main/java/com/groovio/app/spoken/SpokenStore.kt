package com.groovio.app.spoken

import android.content.Context
import com.groovio.app.online.OnlineTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SpokenCollection(val album: com.groovio.app.online.OnlineAlbum, val kind: String, val tracks: List<OnlineTrack>)

data class SpokenItem(val track: OnlineTrack, val kind: String, val positionMs: Long = 0, val inLibrary: Boolean = true)

/** Separate spoken-content library and progress; music history stays music. */
class SpokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("spoken_library", Context.MODE_PRIVATE)
    private val items = MutableStateFlow(decode(prefs.getString("items", "[]").orEmpty()))
    val saved = items.asStateFlow()
    private val collections = MutableStateFlow(decodeCollections(prefs.getString("collections", "[]").orEmpty()))
    val followed = collections.asStateFlow()
    @Synchronized fun follow(album: com.groovio.app.online.OnlineAlbum, kind: String, tracks: List<OnlineTrack>) {
        writeCollections(collections.value.filterNot { it.album.url == album.url } + SpokenCollection(album, kind, tracks))
    }
    @Synchronized fun unfollow(url: String) { writeCollections(collections.value.filterNot { it.album.url == url }) }
    private fun writeCollections(list: List<SpokenCollection>) {
        val json = JSONArray().apply { list.forEach { c -> put(JSONObject().put("url", c.album.url).put("title", c.album.title)
            .put("artist", c.album.artist.orEmpty()).put("art", c.album.thumbnailUrl.orEmpty()).put("kind", c.kind)
            .put("tracks", JSONArray(encode(c.tracks.map { SpokenItem(it, c.kind) })))) } }
        prefs.edit().putString("collections", json.toString()).apply(); collections.value = list
    }
    fun exportCollections(): JSONArray = JSONArray(prefs.getString("collections", "[]"))
    @Synchronized fun restoreCollections(array: JSONArray) {
        writeCollections((collections.value + decodeCollections(array.toString())).distinctBy { it.album.url })
    }
    fun contains(id: String) = items.value.any { it.track.id == id.removePrefix("yt:") }
    fun position(id: String) = items.value.firstOrNull { it.track.id == id.removePrefix("yt:") }?.positionMs ?: 0L
    /** Listening creates a resume entry, not an explicit library save or offline download. */
    @Synchronized fun remember(track: OnlineTrack, kind: String) {
        if (items.value.none { it.track.id == track.id }) write(items.value + SpokenItem(track, kind, inLibrary = false))
    }
    fun save(track: OnlineTrack, kind: String) = saveAll(listOf(track), kind)
    @Synchronized fun saveAll(tracks: List<OnlineTrack>, kind: String) {
        val unique = tracks.distinctBy { it.id }
        val ids = unique.map { it.id }.toSet()
        val existing = items.value.map { if (it.track.id in ids) it.copy(kind = kind, inLibrary = true) else it }
        val known = existing.map { it.track.id }.toSet()
        val additions = unique.filterNot { it.id in known }.map { SpokenItem(it, kind) }
        if (existing != items.value || additions.isNotEmpty()) write(existing + additions)
    }
    /** Removing a bookmark must not erase the listening position. */
    @Synchronized fun remove(id: String) {
        val key = id.removePrefix("yt:")
        write(items.value.mapNotNull {
            if (it.track.id != key) it else if (it.positionMs > 0) it.copy(inLibrary = false) else null
        })
    }
    @Synchronized fun progress(id: String, ms: Long) {
        val key = id.removePrefix("yt:")
        val updated = items.value.map { item ->
            if (item.track.id != key) item else {
                val duration = item.track.durationMs
                val position = ms.coerceAtLeast(0)
                val finished = duration != null && duration > 0 && position >= duration - minOf(5_000L, duration / 100)
                item.copy(positionMs = if (finished) 0 else position)
            }
        }
        if (updated != items.value) write(updated)
    }
    private fun write(list: List<SpokenItem>) {
        prefs.edit().putString("items", encode(list)).apply(); items.value = list
    }
    fun export(): JSONArray = JSONArray(encode(items.value))
    @Synchronized fun restore(array: JSONArray) { write((items.value + decode(array.toString())).distinctBy { it.track.id }) }
    companion object {
        fun decodeCollections(text: String): List<SpokenCollection> = runCatching {
            val a = JSONArray(text); require(a.length() <= 1000)
            (0 until a.length()).map { i -> a.getJSONObject(i).let { j ->
                val url = j.getString("url")
                require(url.startsWith("https://www.youtube.com/playlist?") || url.startsWith("https://music.youtube.com/playlist?"))
                val kind = j.getString("kind"); require(kind in listOf("Podcasts", "Audiobooks"))
                SpokenCollection(com.groovio.app.online.OnlineAlbum(url, j.getString("title").take(2000), j.optString("artist"),
                    j.optString("art").takeIf { it.startsWith("https://") }, "Playlist"), kind, decode(j.getJSONArray("tracks").toString()).map { it.track })
            } }
        }.getOrDefault(emptyList())

        fun encode(items: List<SpokenItem>): String = JSONArray().apply { items.forEach { item ->
            val t = item.track
            put(JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist.orEmpty())
                .put("art", t.thumbnailUrl.orEmpty()).put("duration", t.durationMs ?: 0)
                .put("kind", item.kind).put("position", item.positionMs).put("saved", item.inLibrary))
        } }.toString()
        fun decode(text: String): List<SpokenItem> = runCatching {
            val a = JSONArray(text)
            require(a.length() <= 5000)
            (0 until a.length()).map { i -> a.getJSONObject(i).let { j ->
                val id = j.getString("id"); require(id.matches(Regex("[A-Za-z0-9_-]+")))
                SpokenItem(OnlineTrack(id, j.getString("title").take(2000), j.optString("artist").ifBlank { null },
                    j.optLong("duration").takeIf { it > 0 }, j.optString("art").takeIf { it.startsWith("https://") }),
                    j.getString("kind").also { require(it in listOf("Podcasts", "Audiobooks")) }, j.optLong("position").coerceAtLeast(0),
                    j.optBoolean("saved", true))
            } }
        }.getOrDefault(emptyList())
    }
}
