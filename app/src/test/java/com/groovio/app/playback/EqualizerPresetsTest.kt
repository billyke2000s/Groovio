package com.groovio.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetsTest {
    @Test fun presetsFitFiveBandRange() {
        assertTrue(EqualizerPresets.all.size >= 20)
        assertTrue(EqualizerPresets.all.all { it.bands.size == 5 && it.bands.all { db -> db in -6..6 } })
        assertEquals(EqualizerPresets.all.size, EqualizerPresets.all.map { it.name }.distinct().size)
    }

    @Test fun selectingAndEditingPresetsShowsRealState() {
        val bass = EqualizerPresets.all.first { it.name == "Bass boost" }
        assertEquals("Bass boost", EqualizerPresets.selected(bass.bands))
        assertEquals("Custom", EqualizerPresets.selected(listOf(1, 2, 3, 4, 5)))
        assertEquals("Flat", EqualizerPresets.selected(listOf(0, 0, 0, 0, 0)))
    }

    @Test fun presetsAreHeadroomSafe() {
        EqualizerPresets.all.forEach { preset ->
            val adjusted = EqualizerPolicy.safeLevels(preset.bands)
            assertEquals(5, adjusted.size)
            assertTrue(adjusted.maxOrNull()!! <= 0)
            assertTrue(adjusted.all { it in -12..0 })
        }
    }
}
