package com.groovio.app.online

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
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
