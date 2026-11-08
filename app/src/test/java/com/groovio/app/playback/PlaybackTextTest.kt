package com.groovio.app.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTextTest {

    @Test fun `formats minutes and hours`() {
        assertEquals("0:00", PlaybackText.time(0))
        assertEquals("0:07", PlaybackText.time(7_400))
        assertEquals("3:07", PlaybackText.time(187_000))
        assertEquals("1:02:05", PlaybackText.time(3_725_000))
    }

    @Test fun `unknown or negative time shows zero`() {
        assertEquals("0:00", PlaybackText.time(null))
        assertEquals("0:00", PlaybackText.time(-5_000))
    }

    @Test fun `repeat cycles off, all, one, off`() {
        assertEquals(Player.REPEAT_MODE_ALL, PlaybackText.nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, PlaybackText.nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackText.nextRepeatMode(Player.REPEAT_MODE_ONE))
    }
}
