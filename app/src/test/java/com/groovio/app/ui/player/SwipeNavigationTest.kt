package com.groovio.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeNavigationTest {
    @Test fun normalSwipeMovesToRealAdjacentTrack() {
        assertEquals(-1, SwipeNavigation.direction(-90f, -300f, 360f, true, true))
        assertEquals(1, SwipeNavigation.direction(90f, 300f, 360f, true, true))
    }
    @Test fun tinyDragsAndWrongWayFlicksSnapBack() {
        assertEquals(0, SwipeNavigation.direction(20f, 100f, 360f, true, true))
        assertEquals(0, SwipeNavigation.direction(30f, -2000f, 360f, true, true))
        assertEquals(-1, SwipeNavigation.direction(-30f, -1600f, 360f, true, true))
    }
    @Test fun queueBoundariesNeverRequestInvalidTracks() {
        assertEquals(0, SwipeNavigation.direction(140f, 1600f, 360f, false, true))
        assertEquals(0, SwipeNavigation.direction(-140f, -1600f, 360f, true, false))
    }
}