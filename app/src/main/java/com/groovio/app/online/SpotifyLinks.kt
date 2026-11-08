package com.groovio.app.online

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.util.Base64

/** A Spotify song, album or playlist reference. */
data class SpotifyRef(val type: String, val id: String) {
    val embedUrl: String get() = "https://open.spotify.com/embed/$type/$id"
}

data class SpotifyTrack(
    val title: String,
    /** Comma-separated artists. */
    val artist: String?,
    val durationMs: Long?,
    val uri: String?,
)

data class SpotifyCollection(val name: String, val type: String, val tracks: List<SpotifyTrack>)

/**
 * Reads Spotify links without an account or API key, from Spotify's public embed page,
 * the same way the Discord bot does (via spotify-url-info). Pure parsing, no network.
 */
object SpotifyLinks {

    private val openUrl = Regex(
        "^https?://(?:open|play)\\.spotify\\.com/(?:intl-[a-z-]+/)?(?:embed/)?(track|album|playlist)/([A-Za-z0-9]+)",
        RegexOption.IGNORE_CASE,
    )
    private val spotifyUri = Regex("^spotify:(track|album|playlist):([A-Za-z0-9]+)$")
    private val shortLink = Regex("^https?://spotify\\.link/\\S+$", RegexOption.IGNORE_CASE)
    private val anyLink = Regex("(?:https?://(?:open\\.|play\\.)?spotify\\.(?:com|link)/\\S+|spotify:[a-z]+:[A-Za-z0-9]+)")

    /** Finds a Spotify link in shared text such as "Listen to X on Spotify: https://...". */
    fun findLink(text: String): String? = anyLink.find(text)?.value?.trimEnd('.', ',', ')')

    fun isShortLink(url: String): Boolean = shortLink.matches(url.trim())

    fun parse(link: String): SpotifyRef? {
        val s = link.trim()
        val m = openUrl.find(s) ?: spotifyUri.find(s) ?: return null
        return SpotifyRef(m.groupValues[1].lowercase(), m.groupValues[2])
    }

    /** Parses an embed page; throws [IllegalArgumentException] if its shape is unknown. */
    fun parseEmbed(html: String): SpotifyCollection {
        val entity = entityFrom(html) ?: throw IllegalArgumentException("Couldn't read the Spotify page")
        val type = entity.getString("uri")?.split(':')?.getOrNull(1) ?: entity.getString("type")
        val name = entity.getString("name") ?: entity.getString("title") ?: "Spotify"
        val list = entity.getArray("trackList")
        val tracks = if (list.isNotEmpty()) {
            (0 until list.size).mapNotNull { toTrack(list.getObject(it)) }
        } else {
            listOfNotNull(toTrack(entity))
        }
        return SpotifyCollection(name, type ?: "track", tracks.filterNot { it.uri?.contains(":episode:") == true })
    }

    private fun toTrack(o: JsonObject): SpotifyTrack? {
        val title = o.getString("title") ?: o.getString("name") ?: return null
        val artists = o.getArray("artists").let { a ->
            (0 until a.size).mapNotNull { a.getObject(it).getString("name") }.joinToString(", ")
        }.ifBlank { null }
        return SpotifyTrack(
            title = title,
            artist = artists ?: o.getString("subtitle")?.takeIf { it.isNotBlank() },
            durationMs = o.getLong("duration").takeIf { it > 0 },
            uri = o.getString("uri"),
        )
    }

    private fun entityFrom(html: String): JsonObject? {
        // Newer pages: base64 "resource" or "initial-state" scripts; current pages: __NEXT_DATA__.
        scriptBody(html, "resource")?.let { return decodeBase64Json(it) }
        scriptBody(html, "initial-state")?.let { decodeBase64Json(it)?.getObject("data")?.getObject("entity")?.let { e -> return e } }
        scriptBody(html, "__NEXT_DATA__")?.let { body ->
            val root = runCatching { JsonParser.`object`().from(body) }.getOrNull() ?: return null
            val entity = root.getObject("props").getObject("pageProps").getObject("state")
                .getObject("data").getObject("entity")
            return entity.takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun decodeBase64Json(body: String): JsonObject? = runCatching {
        JsonParser.`object`().from(String(Base64.getDecoder().decode(body.trim()), Charsets.UTF_8))
    }.getOrNull()

    /** Contents of the first <script> whose attributes include [marker] as a value. */
    private fun scriptBody(html: String, marker: String): String? {
        val open = Regex("<script\\b[^>]*=[\"']${Regex.escape(marker)}[\"'][^>]*>", RegexOption.IGNORE_CASE)
        val m = open.find(html) ?: return null
        val end = html.indexOf("</script>", m.range.last + 1)
        return if (end < 0) null else html.substring(m.range.last + 1, end)
    }
}
