package com.groovio.app.playback

import androidx.media3.common.Player
import java.util.Locale

data class QueueEntry(
    val index: Int,
    val title: String,
    val artist: String?,
)

/** Snapshot of the real player state, as the UI sees it. */
data class PlayerUiState(
    val connected: Boolean = false,
    val hasItem: Boolean = false,
    val title: String = "",
    val artist: String? = null,
    val artworkData: ByteArray? = null,
    /** Online artwork, used when there are no embedded artwork bytes. */
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long? = null,
    val hasNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** e.g. "OPUS · 160K · 48kHz"; null when nothing is known. */
    val audioLabel: String? = null,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val currentMediaId: String? = null,
    val error: String? = null,
) {
    // ByteArray uses reference equality, which is what we want: a new artwork
    // array means new artwork. Declared explicitly so the intent is clear.
    override fun equals(other: Any?): Boolean =
        other is PlayerUiState &&
            connected == other.connected && hasItem == other.hasItem &&
            title == other.title && artist == other.artist &&
            artworkData === other.artworkData && artworkUri == other.artworkUri &&
            isPlaying == other.isPlaying &&
            isBuffering == other.isBuffering && durationMs == other.durationMs &&
            hasNext == other.hasNext && shuffle == other.shuffle &&
            repeatMode == other.repeatMode && audioLabel == other.audioLabel &&
            queue == other.queue && currentIndex == other.currentIndex &&
            currentMediaId == other.currentMediaId &&
            error == other.error

    override fun hashCode(): Int =
        listOf(connected, hasItem, title, artist, System.identityHashCode(artworkData), artworkUri,
            isPlaying, isBuffering, durationMs, hasNext, shuffle, repeatMode,
            audioLabel, queue, currentIndex, currentMediaId, error).hashCode()
}

object PlaybackText {
    /** "3:07", "1:02:05"; negative or unknown values become "0:00". */
    fun time(ms: Long?): String {
        val total = ((ms ?: 0L).coerceAtLeast(0L)) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** Off → all → one → off, like most music apps. */
    fun nextRepeatMode(mode: Int): Int = when (mode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }
}
