package com.groovio.app.spoken

import android.content.Context
import com.groovio.app.online.OnlineTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SpokenItem(val track: OnlineTrack, val kind: String, val positionMs: Long = 0)

/** Separate spoken-content library and progress; music history stays music. */
class SpokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("spoken_library", Context.MODE_PRIVATE)
    private val items = MutableStateFlow(decode(prefs.getString("items", "[]").orEmpty()))
    val saved = items.asStateFlow()
    fun contains(id: String) = items.value.any { it.track.id == id.removePrefix("yt:") }
    fun position(id: String) = items.value.firstOrNull { it.track.id == id.removePrefix("yt:") }?.positionMs ?: 0L
    @Synchronized fun save(track: OnlineTrack, kind: String) {
        if (items.value.none { it.track.id == track.id }) write(items.value + SpokenItem(track, kind))
    }
    @Synchronized fun remove(id: String) { write(items.value.filterNot { it.track.id == id }) }
    @Synchronized fun progress(id: String, ms: Long) {
        write(items.value.map { if (it.track.id == id.removePrefix("yt:")) it.copy(positionMs = ms.coerceAtLeast(0)) else it })
    }
    private fun write(list: List<SpokenItem>) {
        prefs.edit().putString("items", encode(list)).apply(); items.value = list
    }
    fun export(): JSONArray = JSONArray(encode(items.value))
    @Synchronized fun restore(array: JSONArray) { write((items.value + decode(array.toString())).distinctBy { it.track.id }) }
    companion object {
        fun encode(items: List<SpokenItem>): String = JSONArray().apply { items.forEach { item ->
            val t = item.track
            put(JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist.orEmpty())
                .put("art", t.thumbnailUrl.orEmpty()).put("duration", t.durationMs ?: 0)
                .put("kind", item.kind).put("position", item.positionMs))
        } }.toString()
        fun decode(text: String): List<SpokenItem> = runCatching {
            val a = JSONArray(text)
            require(a.length() <= 5000)
            (0 until a.length()).map { i -> a.getJSONObject(i).let { j ->
                val id = j.getString("id"); require(id.matches(Regex("[A-Za-z0-9_-]+")))
                SpokenItem(OnlineTrack(id, j.getString("title").take(2000), j.optString("artist").ifBlank { null },
                    j.optLong("duration").takeIf { it > 0 }, j.optString("art").takeIf { it.startsWith("https://") }),
                    j.getString("kind").also { require(it in listOf("Podcasts", "Audiobooks")) }, j.optLong("position").coerceAtLeast(0))
            } }
        }.getOrDefault(emptyList())
    }
}
