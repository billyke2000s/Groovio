package com.groovio.app.playback

/** Five-band sound profiles; actual safe device output is mapped by EqualizerPolicy. */
data class EqualizerPreset(val name: String, val bands: List<Int>)

object EqualizerPresets {
    val all = listOf(
        EqualizerPreset("Flat", listOf(0, 0, 0, 0, 0)),
        EqualizerPreset("Bass boost", listOf(5, 3, 0, -1, -2)),
        EqualizerPreset("Deep bass", listOf(6, 4, 0, -2, -3)),
        EqualizerPreset("Bass reducer", listOf(-5, -3, 0, 1, 2)),
        EqualizerPreset("Treble boost", listOf(-2, -1, 0, 3, 5)),
        EqualizerPreset("Treble reducer", listOf(2, 1, 0, -3, -5)),
        EqualizerPreset("Vocal", listOf(-3, -1, 3, 3, -1)),
        EqualizerPreset("Spoken word", listOf(-5, -2, 3, 4, -2)),
        EqualizerPreset("Acoustic", listOf(2, 1, 0, 3, 2)),
        EqualizerPreset("Classical", listOf(2, 1, 0, 1, 3)),
        EqualizerPreset("Dance", listOf(5, 3, 0, 2, 4)),
        EqualizerPreset("Electronic", listOf(4, 2, -1, 2, 5)),
        EqualizerPreset("Hip-hop", listOf(5, 4, 0, 1, 2)),
        EqualizerPreset("Jazz", listOf(2, 1, 2, 2, 2)),
        EqualizerPreset("Metal", listOf(3, 1, 0, 3, 4)),
        EqualizerPreset("Pop", listOf(-1, 2, 3, 2, -1)),
        EqualizerPreset("R&B", listOf(4, 3, 0, 2, 1)),
        EqualizerPreset("Rock", listOf(3, 1, -1, 2, 3)),
        EqualizerPreset("Warm", listOf(3, 2, 1, -1, -2)),
        EqualizerPreset("Bright", listOf(-2, -1, 0, 3, 5)),
        EqualizerPreset("Loudness", listOf(4, 2, -2, 2, 4)),
        EqualizerPreset("V-shaped", listOf(5, 3, -3, 3, 5)),
    )
    fun selected(bands: List<Int>): String = all.firstOrNull { it.bands == bands }?.name ?: "Custom"
}
