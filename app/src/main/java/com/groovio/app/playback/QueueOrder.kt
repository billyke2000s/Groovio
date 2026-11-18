package com.groovio.app.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

object QueueOrder {
    /** One traversal, without repeat wrapping; indexes still address original MediaItems. */
    fun upcoming(timeline: Timeline, current: Int, shuffle: Boolean): List<Int> {
        if (timeline.isEmpty || current !in 0 until timeline.windowCount) return emptyList()
        val result = mutableListOf<Int>()
        var next = timeline.getNextWindowIndex(current, Player.REPEAT_MODE_OFF, shuffle)
        while (next != C.INDEX_UNSET && next != current && next !in result) {
            result.add(next)
            next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, shuffle)
        }
        return result
    }
}
