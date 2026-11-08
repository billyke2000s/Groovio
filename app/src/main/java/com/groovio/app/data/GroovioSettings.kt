package com.groovio.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User settings, stored on the phone. Read by the UI and the playback service (same process). */
class GroovioSettings(context: Context) {
    private val prefs = context.getSharedPreferences("groovio_settings", Context.MODE_PRIVATE)

    private val _dataSaver = MutableStateFlow(prefs.getBoolean(KEY_DATA_SAVER, false))
    /** Prefer the smallest audio stream instead of the best one. */
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    /** When the queue ends, keep playing similar songs (like Spotify's Autoplay). */
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    fun setDataSaver(on: Boolean) {
        prefs.edit().putBoolean(KEY_DATA_SAVER, on).apply()
        _dataSaver.value = on
    }

    fun setAutoplay(on: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, on).apply()
        _autoplay.value = on
    }

    private companion object {
        const val KEY_DATA_SAVER = "data_saver"
        const val KEY_AUTOPLAY = "autoplay"
    }
}
