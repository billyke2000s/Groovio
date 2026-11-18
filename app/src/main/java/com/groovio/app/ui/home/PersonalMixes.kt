package com.groovio.app.ui.home

import android.content.Context
import com.groovio.app.data.TrackEntity
import com.groovio.app.online.OnlineTrack
import org.json.JSONArray
import org.json.JSONObject

/** A real ordered collection, persisted before being presented as a daily mix. */
data class PersonalMix(val id: String, val title: String, val reason: String, val tracks: List<OnlineTrack>)

object MixSelection {
    fun seeds(liked: List<TrackEntity>, recent: List<TrackEntity>): List<TrackEntity> =
        (recent.take(15) + liked).filter { it.mediaId.startsWith("yt:") }
            .distinctBy { it.artist?.lowercase()?.takeIf(String::isNotBlank) ?: it.mediaId }.take(6)

    /** A song belongs to one daily shelf, so shelves don't repeat the same results. */
    fun diversify(mixes: List<PersonalMix>): List<PersonalMix> {
        val used = mutableSetOf<String>()
        return mixes.map { mix -> mix.copy(tracks = mix.tracks.filter { used.add(it.id) }.take(25)) }
            .filter { it.tracks.isNotEmpty() }
    }
}

class PersonalMixStore(context: Context) {
    private val prefs = context.getSharedPreferences("personal_mixes", Context.MODE_PRIVATE)
    fun read(day: String): List<PersonalMix> = runCatching {
        if (prefs.getString("day", "") != day) return emptyList()
        val a = JSONArray(prefs.getString("mixes", "[]"))
        (0 until a.length()).map { i ->
            val j = a.getJSONObject(i)
            val songs = j.getJSONArray("tracks")
            PersonalMix(j.getString("id"), j.getString("title"), j.getString("reason"),
                (0 until songs.length()).map { n -> songs.getJSONObject(n).let {
                    OnlineTrack(it.getString("id"), it.getString("title"), it.optString("artist").ifBlank { null },
                        it.optLong("duration").takeIf { d -> d > 0 }, it.optString("art").ifBlank { null })
                } })
        }
    }.getOrDefault(emptyList())
    fun write(day: String, mixes: List<PersonalMix>) {
        val a = JSONArray()
        mixes.forEach { mix -> a.put(JSONObject().put("id", mix.id).put("title", mix.title).put("reason", mix.reason)
            .put("tracks", JSONArray().apply { mix.tracks.forEach { t -> put(JSONObject()
                .put("id", t.id).put("title", t.title).put("artist", t.artist.orEmpty())
                .put("duration", t.durationMs ?: 0).put("art", t.thumbnailUrl.orEmpty())) } })) }
        prefs.edit().putString("day", day).putString("mixes", a.toString()).apply()
    }
    fun clear() { prefs.edit().clear().apply() }
}
