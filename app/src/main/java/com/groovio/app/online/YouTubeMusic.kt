package com.groovio.app.online

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/** A song found online. [id] is the YouTube video id. */
data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val durationMs: Long?,
    val thumbnailUrl: String?,
)

/** An album or playlist on YouTube Music. */
data class OnlineAlbum(
    val url: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
    val kind: String = "Album",
)

data class ArtistPage(
    val name: String,
    val songs: List<OnlineTrack>,
    val albums: List<OnlineAlbum>,
    /** Artist photo from YouTube Music, when found. */
    val imageUrl: String? = null,
    val subscribers: Long? = null,
)

/** A resolved, directly playable audio URL. */
data class ResolvedAudio(
    val url: String,
    val codec: String?,
    val bitrate: Int,
    /** Extra request headers the stream host requires. */
    val headers: Map<String, String>,
    val resolvedAtMs: Long,
)

/**
 * YouTube Music search and audio resolution, entirely on-device via NewPipe Extractor.
 * Every function blocks on network I/O: call from a background thread.
 */
class YouTubeMusic(
    private val clock: () -> Long = System::currentTimeMillis,
    /** Settings → Audio quality → Data saver. */
    private val dataSaver: () -> Boolean = { false },
    private val onlineAllowed: () -> Boolean = { true },
) {

    private fun requireOnline() { if (!onlineAllowed()) throw java.io.IOException("Offline mode is enabled") }

    private val service = ServiceList.YouTube
    private val cache = ConcurrentHashMap<String, ResolvedAudio>()

    /** Songs only (YouTube Music "songs" filter), so videos, covers and uploads rank lower. */
    fun searchSpokenCollections(query: String): List<OnlineAlbum> {
        requireOnline()
        val extractor = service.getSearchExtractor(query, listOf(YoutubeSearchQueryHandlerFactory.PLAYLISTS), "")
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().map {
            OnlineAlbum(it.url, it.name, it.uploaderName, bestImage(it.thumbnails), "Playlist")
        }
    }

    fun searchSpoken(query: String): List<OnlineTrack> {
        requireOnline()
        val extractor = service.getSearchExtractor(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull(::toTrack)
    }

    fun searchSongs(query: String): List<OnlineTrack> {
        requireOnline()
        val extractor = service.getSearchExtractor(
            query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "",
        )
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull(::toTrack)
    }

    fun searchAlbums(query: String): List<OnlineAlbum> {
        requireOnline()
        val extractor = service.getSearchExtractor(
            query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), "",
        )
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<PlaylistInfoItem>()
            .map {
                OnlineAlbum(
                    url = it.url,
                    title = it.name,
                    artist = it.uploaderName?.removeSuffix(" - Topic")?.takeIf { a -> a.isNotBlank() },
                    thumbnailUrl = bestImage(it.thumbnails),
                )
            }
    }

    fun searchPlaylists(query: String): List<OnlineAlbum> {
        requireOnline()
        val extractor = service.getSearchExtractor(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS), "")
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().map {
            OnlineAlbum(it.url, it.name, it.uploaderName, bestImage(it.thumbnails), "Playlist")
        }
    }

    /** Full collection in order. Do not silently download just its first page. */
    fun albumTracks(album: OnlineAlbum): List<OnlineTrack> {
        requireOnline()
        val info = PlaylistInfo.getInfo(service, album.url)
        val items = info.relatedItems.filterIsInstance<StreamInfoItem>().toMutableList()
        var next = info.nextPage
        val seenPages = mutableSetOf<String>()
        var pages = 0
        while (org.schabi.newpipe.extractor.Page.isValid(next)) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Cancelled")
            require(++pages <= 50 && items.size < 5000) { "This playlist is too large; try a smaller playlist." }
            val key = next.url.orEmpty() + next.id.orEmpty() + next.ids.orEmpty().joinToString()
            require(seenPages.add(key)) { "Playlist returned a repeated page. Try again later." }
            val page = PlaylistInfo.getMoreItems(service, album.url, next)
            items.addAll(page.items)
            next = page.nextPage
        }
        return items.mapNotNull(::toTrack).map { t ->
            t.copy(artist = t.artist ?: album.artist, thumbnailUrl = if (album.kind == "Album") album.thumbnailUrl ?: t.thumbnailUrl else t.thumbnailUrl)
        }
    }

    /** Top songs and albums by an artist (YouTube Music search, filtered to that artist). */
    fun artist(name: String): ArtistPage {
        requireOnline()
        // The artist entry gives the photo; a failure there must not hide songs/albums.
        val channel = runCatching {
            val extractor = service.getSearchExtractor(
                name, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS), "",
            )
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<ChannelInfoItem>()
                .firstOrNull { sameArtist(name, it.name) }
        }.getOrNull()
        return ArtistPage(
            name = channel?.name ?: name,
            songs = searchSongs(name).filter { sameArtist(name, it.artist) }.take(10),
            albums = searchAlbums(name).filter { sameArtist(name, it.artist) }.take(12),
            imageUrl = channel?.thumbnails?.let(::bestImage)?.let { largeArtwork(it, 900) },
            subscribers = channel?.subscriberCount?.takeIf { it > 0 },
        )
    }

    private val relatedCache = ConcurrentHashMap<String, List<OnlineTrack>>()

    /** Songs YouTube suggests after [videoId] (its "up next" list), song-length only. */
    fun related(videoId: String): List<OnlineTrack> = relatedCache.getOrPut(videoId) {
        requireOnline()
        val info = StreamInfo.getInfo(service, service.streamLHFactory.fromId(videoId).url)
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 60..900 }
            .mapNotNull(::toTrack)
            .filter { it.id != videoId }
            .distinctBy { it.id }
            .take(20)
    }

    /** The Discord bot's way of getting audio (TV / Android VR clients), on the phone. */
    private val innertube = InnertubeAudio(clock = clock)

    fun resolve(videoId: String): ResolvedAudio {
        requireOnline()
        val saver = dataSaver()
        val key = if (saver) "$videoId|saver" else videoId
        cache[key]?.let { if (clock() - it.resolvedAtMs < URL_TTL_MS) return it }
        // 1) The bot's method; 2) NewPipe's own extraction as a fallback.
        val first = runCatching { innertube.resolve(videoId, saver) }
        first.getOrNull()?.let { cache[key] = it; return it }
        return try {
            resolveWithNewPipe(videoId, saver).also { cache[key] = it }
        } catch (e: Exception) {
            // Report the bot-method error if NewPipe failed too, so "blocked" is shown accurately.
            first.exceptionOrNull()?.let { e.addSuppressed(it) }
            throw e
        }
    }

    private fun resolveWithNewPipe(videoId: String, saver: Boolean): ResolvedAudio {
        val url = service.streamLHFactory.fromId(videoId).url
        val info = StreamInfo.getInfo(service, url)
        val chosen = StreamSelector.choose(
            info.audioStreams.map {
                AudioCandidate(
                    url = if (it.isUrl) it.content else "",
                    codec = it.codec,
                    bitrate = if (it.averageBitrate > 0) it.averageBitrate else it.bitrate,
                    progressive = it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP,
                    originalTrack = it.audioTrackType == null ||
                        it.audioTrackType == AudioTrackType.ORIGINAL,
                )
            }, saver,
        )
        // Some long recordings have no separate audio representation at all.
        // NewPipe lists progressive video+audio files in videoStreams. Only use
        // these if audio-only is genuinely absent; the service disables the
        // video track so Groovio never starts a video renderer.
        val muxed = if (chosen == null) MuxedAudioFallback.choose(info.videoStreams.map {
            MuxedAudioFallback.Candidate(
                url = if (it.isUrl) it.content else "",
                resolution = it.resolution,
                progressive = it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP,
            )
        }) else null
        val streamUrl = chosen?.url ?: muxed?.url ?: throw NoPlayableAudioException(videoId)
        // Each YouTube client's stream URLs expect that client's user agent.
        val userAgent = when {
            YoutubeParsingHelper.isVisionOsStreamingUrl(streamUrl) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            YoutubeParsingHelper.isAndroidStreamingUrl(streamUrl) -> YoutubeParsingHelper.getAndroidUserAgent(null)
            YoutubeParsingHelper.isIosStreamingUrl(streamUrl) -> YoutubeParsingHelper.getIosUserAgent(null)
            else -> null
        }
        val headers = userAgent?.let { mapOf("User-Agent" to it) } ?: emptyMap()
        // A muxed stream's actual audio codec is measured by Media3, not guessed.
        return ResolvedAudio(streamUrl, chosen?.codec, chosen?.bitrate ?: 0, headers, clock())
    }

    /** Forget a cached URL, e.g. after the host rejected it as expired. */
    /** Forget all cached stream URLs (e.g. after changing audio quality). */
    fun clearCache() = cache.clear()

    fun invalidate(videoId: String) {
        cache.remove(videoId)
        cache.remove("$videoId|saver")
    }

    private fun toTrack(item: StreamInfoItem): OnlineTrack? {
        val id = try {
            service.streamLHFactory.fromUrl(item.url).id
        } catch (_: Exception) {
            return null
        }
        return OnlineTrack(
            id = id,
            title = item.name,
            artist = item.uploaderName?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() },
            durationMs = item.duration.takeIf { it > 0 }?.times(1000),
            thumbnailUrl = bestImage(item.thumbnails),
        )
    }

    companion object {
        /** YouTube stream URLs last about six hours; refresh well before that. */
        const val URL_TTL_MS = 4 * 60 * 60 * 1000L

        /** "Daft Punk" vs "Daft Punk, Pharrell Williams": loose, accent/case-insensitive. */
        fun sameArtist(wanted: String, actual: String?): Boolean {
            val a = TrackMatcher.compact(actual)
            if (a.isEmpty()) return false
            return wanted.split(',').map { TrackMatcher.compact(it) }.filter { it.isNotEmpty() }
                .any { w -> a == w || a.startsWith(w) || w.startsWith(a) }
        }

        fun bestImage(images: List<Image>): String? =
            images.maxByOrNull { it.height }?.url?.let(::largeArtwork)

        private val googleSize = Regex("=w\\d+-h\\d+")

        /**
         * YouTube Music returns 120px song art; Google's image server can serve the same
         * image larger by changing the size parameter (e.g. "=w120-h120-l90-rj").
         */
        fun largeArtwork(url: String, size: Int = 544): String =
            if (url.contains("googleusercontent.com")) url.replaceFirst(googleSize, "=w$size-h$size")
            else url
    }
}

class NoPlayableAudioException(videoId: String) :
    Exception("No playable audio stream for $videoId")
