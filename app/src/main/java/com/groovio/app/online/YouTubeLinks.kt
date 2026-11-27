package com.groovio.app.online

import java.net.URI

/** Recognise pasted YouTube video links without treating arbitrary text as a URL. */
object YouTubeLinks {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")

    fun videoId(input: String): String? {
        val raw = input.trim()
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("https", "http")) return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val path = uri.path.orEmpty().trim('/')
        val candidate = when (host) {
            "youtu.be" -> path.takeIf { '/' !in it }
            "youtube.com", "m.youtube.com", "music.youtube.com" -> when {
                path == "watch" -> uri.rawQuery.orEmpty().split('&')
                    .firstOrNull { it.substringBefore('=') == "v" }?.substringAfter('=')
                path.startsWith("shorts/") || path.startsWith("live/") ->
                    path.substringAfter('/').substringBefore('/')
                else -> null
            }
            else -> null
        }
        return candidate?.takeIf { idPattern.matches(it) }
    }
}
