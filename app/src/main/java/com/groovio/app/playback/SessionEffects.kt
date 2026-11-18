package com.groovio.app.playback

import android.media.audiofx.Equalizer
import kotlin.math.abs
import kotlin.math.ln

/** Boosts use relative attenuation, retaining headroom without changing player volume. */
object EqualizerPolicy {
    val frequencies = listOf(60, 230, 910, 3600, 14000)
    fun safeLevels(levels: List<Int>): List<Int> {
        val bounded = frequencies.indices.map { levels.getOrElse(it) { 0 }.coerceIn(-6, 6) }
        val headroom = bounded.maxOrNull()?.coerceAtLeast(0) ?: 0
        return bounded.map { it - headroom }
    }
    fun nearest(frequency: Int) = frequencies.indices.minBy { abs(ln(frequencies[it].toDouble() / frequency.coerceAtLeast(1))) }
}

interface SessionEffect {
    fun configure(levels: List<Int>)
    fun release()
}

/** Nothing is instantiated when disabled. Failure cannot stop or recreate the player. */
class SessionEffects(private val create: (Int) -> SessionEffect = { session -> PlatformEqualizer(session) }) {
    private var effect: SessionEffect? = null
    private var session = 0
    fun apply(sessionId: Int, enabled: Boolean, levels: List<Int>): String? {
        if (!enabled || sessionId <= 0) { close(); return null }
        return try {
            if (session != sessionId || effect == null) { close(); effect = create(sessionId); session = sessionId }
            effect?.configure(EqualizerPolicy.safeLevels(levels)); null
        } catch (_: Exception) { close(); "Equaliser unavailable for this audio output. Playback continues without it." }
    }
    fun close() { runCatching { effect?.release() }; effect = null; session = 0 }
}

@Suppress("DEPRECATION")
private class PlatformEqualizer(session: Int) : SessionEffect {
    private val equalizer = Equalizer(0, session)
    override fun configure(levels: List<Int>) {
        val range = equalizer.bandLevelRange
        for (band in 0 until equalizer.numberOfBands.toInt()) {
            val hz = equalizer.getCenterFreq(band.toShort()) / 1000
            val db = levels[EqualizerPolicy.nearest(hz)]
            equalizer.setBandLevel(band.toShort(), (db * 100).coerceIn(range[0].toInt(), range[1].toInt()).toShort())
        }
        equalizer.enabled = true
    }
    override fun release() { equalizer.release() }
}
