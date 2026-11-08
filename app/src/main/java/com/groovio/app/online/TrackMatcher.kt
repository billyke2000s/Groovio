package com.groovio.app.online

import java.text.Normalizer
import kotlin.math.abs

/**
 * Scores how likely a search result is the exact recording wanted (same artist, title,
 * length, album) and penalises alternate versions (sped up, cover, live...).
 *
 * Ported from the Ultimate Discord Bot's `matchScore` (src/lib/music.js) so Groovio
 * resolves Spotify songs the same way, without the bot or Lavalink.
 */
object TrackMatcher {

    data class Wanted(
        val title: String,
        /** Comma-separated artist names, as Spotify lists them. */
        val artists: String?,
        val lengthMs: Long? = null,
        val album: String? = null,
    )

    data class Candidate(
        val title: String,
        /** Channel / uploader name. */
        val author: String?,
        val lengthMs: Long?,
        val isLive: Boolean = false,
    )

    /** Score at which a result is trusted without trying further searches. */
    const val GOOD_MATCH = 70.0

    /** Versions that are not the original song unless the wanted title says so. */
    private val altVersions = listOf(
        "sped up", "speed up", "slowed", "nightcore", "reverb", "8d", "bass boosted",
        "karaoke", "instrumental", "cover", "remix", "live", "acapella", "a cappella",
        "1 hour", "1hr", "hour", "loop", "looped", "extended", "reaction",
        "piano version", "lyrics video",
    )

    /** Album words that say nothing about which album it is. */
    private val albumFiller = setOf(
        "the", "and", "original", "soundtrack", "ost", "set", "deluxe", "edition", "version",
        "ver", "remaster", "remastered", "digital", "expanded", "anniversary", "vol", "volume",
        "part", "disc", "music", "from", "motion", "picture", "game", "video", "series", "songs",
        "collection", "greatest", "hits", "best", "album", "single", "ep",
    )

    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val marks = Regex("\\p{Mn}+")

    fun norm(s: String?): String =
        Normalizer.normalize(s ?: "", Normalizer.Form.NFKD).replace(marks, "").lowercase()

    fun words(s: String?): List<String> = norm(s).split(nonWord).filter { it.isNotEmpty() }

    fun compact(s: String?): String = words(s).joinToString("")

    fun albumWords(album: String?): List<String> =
        words(album).filter { it.length >= 4 && it !in albumFiller }.distinct()

    /** "Catherine & Catherine Full Body Soundtrack Set" -> "catherine full body". */
    fun albumQuery(album: String?): String = albumWords(album).take(4).joinToString(" ")

    fun score(want: Wanted, r: Candidate): Double {
        var score = 0.0
        val ytTitle = norm(r.title)
        val channel = compact(r.author?.replace(Regex("- topic$", RegexOption.IGNORE_CASE), ""))
        val artists = (want.artists ?: "").split(',').map(::compact).filter { it.length >= 2 }

        // Artist: the channel is the artist ("Artist - Topic", ArtistVEVO), or named in the title.
        if (artists.any { channel.isNotEmpty() && (channel.contains(it) || it.contains(channel)) }) {
            score += 40
        } else if (artists.any { compact(r.title).contains(it) }) {
            score += 25
        }

        // Title: share of the wanted title's words found in the result title.
        val tw = words(want.title).filter { it.length > 1 || it.any(Char::isDigit) }
        if (tw.isNotEmpty()) score += 30.0 * tw.count { ytTitle.contains(it) } / tw.size

        // Length: the same recording is within a few seconds.
        if (want.lengthMs != null && want.lengthMs > 0 && r.lengthMs != null && r.lengthMs > 0) {
            val d = abs(r.lengthMs - want.lengthMs) / 1000.0
            score += when {
                d <= 3 -> 25
                d <= 8 -> 15
                d <= 15 -> 5
                d <= 30 -> -10
                else -> -30
            }
        }

        // Album: fan uploads of soundtracks name the game/film/album instead of the artist.
        val aw = albumWords(want.album)
        if (aw.isNotEmpty()) {
            val hay = " ${words("${r.title} ${r.author ?: ""}").joinToString(" ")} "
            val hit = aw.count { hay.contains(" $it ") }
            if (hit > 0) score += 20 + 5.0 * hit / aw.size
        }

        // Whole-word check, so "live" doesn't match "alive".
        fun has(text: String, phrase: String) = " ${words(text).joinToString(" ")} ".contains(" $phrase ")
        for (v in altVersions) if (has(r.title, v) && !has(want.title, v)) score -= 40
        if (r.isLive) score -= 50
        return score
    }

    /** Best candidate and its score, or null for an empty list. */
    fun best(want: Wanted, candidates: List<Candidate>): Pair<Int, Double>? =
        candidates.mapIndexed { i, c -> i to score(want, c) }.maxByOrNull { it.second }
}
