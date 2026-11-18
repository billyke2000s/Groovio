package com.groovio.app.online

import okhttp3.OkHttpClient
import okhttp3.Request
import com.grack.nanojson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Apple's published UK song chart is metadata only. Audio still comes from YouTube. */
class ChartDiscovery {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    fun ukChart(): SpotifyCollection {
        val request = Request.Builder().url("https://itunes.apple.com/gb/rss/topsongs/limit=20/json").build()
        val text = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Couldn't load chart")
            response.body?.string() ?: throw IOException("Empty chart")
        }
        return parse(text)
    }
    companion object {
        fun parse(text: String): SpotifyCollection {
            val entries = JsonParser.`object`().from(text).getObject("feed").getArray("entry")
            val tracks = (0 until entries.size).map { i ->
                val item = entries.getObject(i)
                SpotifyTrack(title = item.getObject("im:name").getString("label"),
                    artist = item.getObject("im:artist").getString("label"), durationMs = null, uri = null)
            }
            require(tracks.isNotEmpty()) { "Chart is empty" }
            return SpotifyCollection("UK top songs · Apple", "playlist", tracks)
        }
    }
}
