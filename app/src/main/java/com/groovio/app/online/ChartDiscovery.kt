package com.groovio.app.online

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Apple's published UK song chart is metadata only. Audio still comes from YouTube. */
class ChartDiscovery {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    fun ukChart(): SpotifyCollection {
        val request = Request.Builder().url("https://itunes.apple.com/gb/rss/topsongs/limit=20/json").build()
        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Couldn't load chart")
            JSONObject(response.body?.string() ?: throw IOException("Empty chart"))
        }
        val entries = json.getJSONObject("feed").getJSONArray("entry")
        val tracks = (0 until entries.length()).map { i ->
            val item = entries.getJSONObject(i)
            SpotifyTrack(title = item.getJSONObject("im:name").getString("label"),
                artist = item.getJSONObject("im:artist").getString("label"), durationMs = null, uri = null)
        }
        return SpotifyCollection("UK top songs · Apple", "playlist", tracks)
    }
}
