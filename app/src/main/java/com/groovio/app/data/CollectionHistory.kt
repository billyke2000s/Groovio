package com.groovio.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Collection context is independent from the per-song events used by recommendations. */
data class RecentCollection(
    val id: String,
    val title: String,
    val kind: String,
    val artworkUri: String?,
    val tracks: List<TrackEntity>,
    val playlistId: Long? = null,
    val source: String? = null,
    val playedAt: Long = System.currentTimeMillis(),
)

class CollectionHistory(context: Context) {
    private val prefs = context.getSharedPreferences("collection_history", Context.MODE_PRIVATE)
    private var clearedAt = prefs.getLong("cleared_at", 0)
    private val mutable = MutableStateFlow(decode(prefs.getString("items", "[]").orEmpty()))
    val recent = mutable.asStateFlow()
    @Synchronized fun record(item: RecentCollection) {
        if (item.kind !in listOf("Playlist", "Album", "Mix", "Show", "Audiobook") || item.tracks.isEmpty() || item.playedAt <= clearedAt) return
        if (mutable.value.any { it.id == item.id && it.playedAt > item.playedAt }) return
        val list = (mutable.value.filterNot { it.id == item.id } + item).sortedByDescending { it.playedAt }.take(20)
        prefs.edit().putString("items", encode(list).toString()).apply(); mutable.value = list
    }
    @Synchronized fun clear() {
        clearedAt = System.currentTimeMillis()
        prefs.edit().putLong("cleared_at", clearedAt).putString("items", "[]").apply(); mutable.value = emptyList()
    }
    fun export(): JSONArray = encode(mutable.value)
    @Synchronized fun restore(array: JSONArray) {
        val list = (mutable.value + decode(array.toString()).map { if (it.playlistId != null) it.copy(id = "restored:${it.id}", playlistId = null) else it }).groupBy { it.id }.map { (_, items) -> items.maxBy { it.playedAt } }
            .sortedByDescending { it.playedAt }.take(20)
        prefs.edit().putString("items", encode(list).toString()).apply(); mutable.value = list
    }
    companion object {
        fun encode(items: List<RecentCollection>) = JSONArray().apply { items.forEach { c ->
            put(JSONObject().put("id", c.id).put("title", c.title).put("kind", c.kind).put("art", c.artworkUri.orEmpty())
                .put("playlist", c.playlistId ?: -1).put("source", c.source.orEmpty()).put("at", c.playedAt)
                .put("tracks", JSONArray().apply { c.tracks.forEach { t -> put(JSONObject().put("id", t.mediaId)
                    .put("title", t.title).put("artist", t.artist.orEmpty()).put("art", t.artworkUri.orEmpty())) } }))
        } }
        fun decode(text: String): List<RecentCollection> = runCatching {
            val array = JSONArray(text)
            (0 until minOf(array.length(), 20)).mapNotNull { i -> runCatching {
                val j = array.getJSONObject(i); val songs = j.getJSONArray("tracks")
                require(songs.length() <= 5000)
                val tracks = (0 until songs.length()).map { n -> songs.getJSONObject(n).let { t ->
                    val id = t.getString("id"); require(id.startsWith("content://") || id.matches(Regex("yt:[A-Za-z0-9_-]+")))
                    TrackEntity(id, t.getString("title").take(2000), t.optString("artist").ifBlank { null }, art(t.optString("art")))
                } }
                require(tracks.isNotEmpty())
                val kind = j.getString("kind"); require(kind in listOf("Playlist", "Album", "Mix", "Show", "Audiobook"))
                RecentCollection(j.getString("id").take(2000), j.getString("title").take(2000), kind,
                    art(j.optString("art")), tracks, j.optLong("playlist", -1).takeIf { it >= 0 },
                    j.optString("source").takeIf { it.startsWith("https://") }, j.getLong("at"))
            }.getOrNull() }
        }.getOrDefault(emptyList())
        private fun art(value: String) = value.takeIf { it.startsWith("https://") || it.startsWith("content://") }
    }
}
