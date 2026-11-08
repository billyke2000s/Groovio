package com.groovio.app.online

import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Spotify link -> list of songs -> each matched to a YouTube recording, like the Discord bot:
 * search YouTube Music songs first, then regular YouTube, score every result with
 * [TrackMatcher] and keep the best. Blocking network calls: use a background thread.
 */
class SpotifyImporter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val service = ServiceList.YouTube

    fun load(link: String): SpotifyCollection {
        var url = link.trim()
        if (SpotifyLinks.isShortLink(url)) url = expand(url)
        val ref = SpotifyLinks.parse(url) ?: throw IllegalArgumentException("Not a Spotify song, album or playlist link")
        val html = get(ref.embedUrl)
        val collection = SpotifyLinks.parseEmbed(html)
        if (collection.tracks.isEmpty()) throw IllegalArgumentException("That Spotify link has no songs")
        return collection
    }

    /** Best YouTube match for [track], or null if nothing plausible was found. */
    fun match(track: SpotifyTrack): OnlineTrack? {
        val want = TrackMatcher.Wanted(track.title, track.artist, track.durationMs)
        val query = listOfNotNull(track.title, track.artist?.substringBefore(',')).joinToString(" ")
        var best: Pair<OnlineTrack, Double>? = null

        fun tryFilter(filter: String, bonus: Double): Boolean {
            val items = runCatching { search(query, filter) }.getOrDefault(emptyList()).take(8)
            for (item in items) {
                val candidate = TrackMatcher.Candidate(
                    title = item.name,
                    author = item.uploaderName,
                    lengthMs = item.duration.takeIf { it > 0 }?.times(1000),
                )
                val score = TrackMatcher.score(want, candidate) + bonus
                if (best == null || score > best!!.second) {
                    toTrack(item)?.let { best = it to score }
                }
            }
            return (best?.second ?: Double.NEGATIVE_INFINITY) >= TrackMatcher.GOOD_MATCH
        }

        // YouTube Music "songs" are official audio; small bonus as in the bot.
        if (!tryFilter(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, 3.0)) {
            tryFilter(YoutubeSearchQueryHandlerFactory.VIDEOS, 0.0)
        }
        return best?.takeIf { it.second >= MIN_ACCEPTABLE }?.first
    }

    private fun search(query: String, filter: String): List<StreamInfoItem> {
        val extractor = service.getSearchExtractor(query, listOf(filter), "")
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
    }

    private fun toTrack(item: StreamInfoItem): OnlineTrack? {
        val id = runCatching { service.streamLHFactory.fromUrl(item.url).id }.getOrNull() ?: return null
        return OnlineTrack(
            id = id,
            title = item.name,
            artist = item.uploaderName?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() },
            durationMs = item.duration.takeIf { it > 0 }?.times(1000),
            thumbnailUrl = YouTubeMusic.bestImage(item.thumbnails),
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", NewPipeDownloader.USER_AGENT).build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Spotify answered ${r.code}")
            return r.body?.string() ?: throw IOException("Empty Spotify page")
        }
    }

    /** spotify.link short links redirect to open.spotify.com. */
    private fun expand(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", NewPipeDownloader.USER_AGENT).build()
        client.newCall(request).execute().use { r -> return r.request.url.toString() }
    }

    companion object {
        /** Below this, a "best" result is more likely wrong than right; skip the song. */
        const val MIN_ACCEPTABLE = 45.0
    }
}
