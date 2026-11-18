package com.groovio.app.playback

import androidx.media3.common.C
import androidx.media3.common.Timeline
import org.junit.Assert.*
import org.junit.Test

class QueueOrderTest {
    private val timeline = object : Timeline() {
        private val order = listOf(2, 0, 3, 1)
        override fun getWindowCount() = 4
        override fun getPeriodCount() = 4
        override fun getWindow(index: Int, window: Window, projection: Long): Window = window
        override fun getPeriod(index: Int, period: Period, setIds: Boolean): Period = period
        override fun getIndexOfPeriod(uid: Any) = uid as Int
        override fun getUidOfPeriod(index: Int): Any = index
        override fun getNextWindowIndex(index: Int, repeatMode: Int, shuffle: Boolean): Int {
            val sequence = if (shuffle) order else listOf(0, 1, 2, 3)
            return sequence.getOrNull(sequence.indexOf(index) + 1) ?: C.INDEX_UNSET
        }
    }
    @Test fun followsActualShuffleOrder() { assertEquals(listOf(0, 3, 1), QueueOrder.upcoming(timeline, 2, true)) }
    @Test fun normalOrderStopsAtEnd() { assertEquals(listOf(2, 3), QueueOrder.upcoming(timeline, 1, false)) }
    @Test fun emptyQueueIsSafe() { assertTrue(QueueOrder.upcoming(Timeline.EMPTY, -1, false).isEmpty()) }
}
