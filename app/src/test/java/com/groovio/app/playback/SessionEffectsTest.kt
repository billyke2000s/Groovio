package com.groovio.app.playback

import org.junit.Assert.*
import org.junit.Test

class SessionEffectsTest {
    @Test fun `disabled effects never create an effect or modify player path`() {
        val effects = SessionEffects { error("Disabled path must not create native effects") }
        assertNull(effects.apply(42, false, listOf(6, 6, 6, 6, 6)))
    }
    @Test fun `enabling and changing sessions releases old effects`() {
        var created = 0; var released = 0
        val effects = SessionEffects { created++; object : SessionEffect {
            override fun configure(levels: List<Int>) { assertTrue(levels.all { it <= 0 }) }
            override fun release() { released++ }
        } }
        effects.apply(1, true, listOf(6, 0, 0, 0, 0))
        effects.apply(2, true, listOf(0, 0, 0, 0, 0))
        effects.apply(2, false, emptyList())
        assertEquals(2, created); assertEquals(2, released)
    }
    @Test fun `unsupported output leaves effects bypassed`() {
        val effects = SessionEffects { throw UnsupportedOperationException() }
        assertNotNull(effects.apply(42, true, emptyList()))
        assertNull(effects.apply(42, false, emptyList()))
    }
    @Test fun `boosts retain relative shape without exceeding zero gain`() {
        assertEquals(listOf(0, -3, -6, -9, -12), EqualizerPolicy.safeLevels(listOf(6, 3, 0, -3, -6)))
    }
}
