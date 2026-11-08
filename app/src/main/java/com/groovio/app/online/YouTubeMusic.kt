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

/** An album on YouTube Music (a playlist under the hood). */
data class OnlineAlbum(
    val url: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
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
class YouTubeMusic(private val clock: () -> Long = System::currentTimeMillis) {

    private val service = ServiceList.YouTube
    private val cache = ConcurrentHashMap<String, ResolvedAudio>()

    /** Songs only (YouTube Music "songs" filter), so videos, covers and uploads rank lower. */
    fun searchSongs(query: String): List<OnlineTrack> {
        val extractor = service.getSearchExtractor(
            query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "",
        )
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull(::toTrack)
    }

    fun searchAlbums(query: String): List<OnlineAlbum> {
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

    /** An album's songs, in order. Songs take the album's square artwork. */
    fun albumTracks(album: OnlineAlbum): List<OnlineTrack> {
        val info = PlaylistInfo.getInfo(service, album.url)
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull(::toTrack)
            .map { t -> t.copy(artist = t.artist ?: album.artist, thumbnailUrl = album.thumbnailUrl ?: t.thumbnailUrl) }
    }

    /** Top songs and albums by an artist (YouTube Music search, filtered to that artist). */
    fun artist(name: String): ArtistPage {
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
        val info = StreamInfo.getInfo(service, service.streamLHFactory.fromId(videoId).url)
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 60..900 }
            .mapNotNull(::toTrack)
            .filter { it.id != videoId }
            .distinctBy { it.id }
            .take(20)
    }

    fun resolve(videoId: String): ResolvedAudio {
        cache[videoId]?.let { if (clock() - it.resolvedAtMs < URL_TTL_MS) return it }
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
            }
        ) ?: throw NoPlayableAudioException(videoId)
        val headers = if (YoutubeParsingHelper.isVisionOsStreamingUrl(chosen.url)) {
            mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(null))
        } else {
            emptyMap()
        }
        return ResolvedAudio(chosen.url, chosen.codec, chosen.bitrate, headers, clock())
            .also { cache[videoId] = it }
    }

    /** Forget a cached URL, e.g. after the host rejected it as expired. */
    fun invalidate(videoId: String) {
        cache.remove(videoId)
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
