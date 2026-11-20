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

    private val _downloadsEnabled = MutableStateFlow(prefs.getBoolean("downloads", true))
    val downloadsEnabled = _downloadsEnabled.asStateFlow()
    private val _wifiOnly = MutableStateFlow(prefs.getBoolean("wifi", true))
    val wifiOnly = _wifiOnly.asStateFlow()
    private val _normalise = MutableStateFlow(prefs.getBoolean("normalise", false))
    val normalise = _normalise.asStateFlow()
    private val _skipFailed = MutableStateFlow(prefs.getBoolean("skip_failed", false))
    val skipFailed = _skipFailed.asStateFlow()
    private val _offlineOnly = MutableStateFlow(prefs.getBoolean("offline_only", false))
    val offlineOnly = _offlineOnly.asStateFlow()
    fun setDownloadsEnabled(on: Boolean) { prefs.edit().putBoolean("downloads", on).apply(); _downloadsEnabled.value = on }
    fun setWifiOnly(on: Boolean) { prefs.edit().putBoolean("wifi", on).apply(); _wifiOnly.value = on }
    fun setNormalise(on: Boolean) { prefs.edit().putBoolean("normalise", on).apply(); _normalise.value = on }
    fun setSkipFailed(on: Boolean) { prefs.edit().putBoolean("skip_failed", on).apply(); _skipFailed.value = on }
    fun setOfflineOnly(on: Boolean) { prefs.edit().putBoolean("offline_only", on).apply(); _offlineOnly.value = on }
    /** Landscape is visual-only. The audio service is never switched or reconfigured. */
    private val _landscapeVideoMode = MutableStateFlow(
        prefs.getString("landscape_video_mode", "wifi")?.takeIf { it in setOf("off", "wifi", "always") } ?: "wifi"
    )
    val landscapeVideoMode = _landscapeVideoMode.asStateFlow()
    fun setLandscapeVideoMode(mode: String) {
        require(mode in setOf("off", "wifi", "always"))
        prefs.edit().putString("landscape_video_mode", mode).apply()
        _landscapeVideoMode.value = mode
    }
    private val _prepareNext = MutableStateFlow(prefs.getBoolean("prepare_next", false))
    val prepareNext = _prepareNext.asStateFlow()
    fun setPrepareNext(on: Boolean) { prefs.edit().putBoolean("prepare_next", on).apply(); _prepareNext.value = on }
    private val _spokenSpeed = MutableStateFlow(prefs.getFloat("spoken_speed", 1f).coerceIn(0.5f, 2f))
    val spokenSpeed = _spokenSpeed.asStateFlow()
    fun setSpokenSpeed(speed: Float) { val s = speed.coerceIn(0.5f, 2f); prefs.edit().putFloat("spoken_speed", s).apply(); _spokenSpeed.value = s }
    private val _equalizerEnabled = MutableStateFlow(prefs.getBoolean("equalizer_enabled", false))
    val equalizerEnabled = _equalizerEnabled.asStateFlow()
    private val _equalizerBands = MutableStateFlow((0..4).map { prefs.getInt("eq_$it", 0).coerceIn(-6, 6) })
    val equalizerBands = _equalizerBands.asStateFlow()
    fun setEqualizerEnabled(on: Boolean) { prefs.edit().putBoolean("equalizer_enabled", on).apply(); _equalizerEnabled.value = on }
    fun setEqualizerBands(bands: List<Int>) {
        val values = (0..4).map { bands.getOrElse(it) { 0 }.coerceIn(-6, 6) }
        prefs.edit().apply { values.forEachIndexed { i, value -> putInt("eq_$i", value) } }.apply()
        _equalizerBands.value = values
    }
    private val _experimentalUpdates = MutableStateFlow(prefs.getBoolean("experimental_updates", false))
    val experimentalUpdates = _experimentalUpdates.asStateFlow()
    fun setExperimentalUpdates(on: Boolean) {
        prefs.edit().putBoolean("experimental_updates", on).apply()
        _experimentalUpdates.value = on
    }
    fun export(): org.json.JSONObject = org.json.JSONObject().apply {
        put("dataSaver", dataSaver.value); put("autoplay", autoplay.value)
        put("downloads", downloadsEnabled.value); put("wifi", wifiOnly.value)
        put("prepareNext", prepareNext.value); put("spokenSpeed", spokenSpeed.value)
        put("normalise", normalise.value); put("skipFailed", skipFailed.value)
        put("offlineOnly", offlineOnly.value); put("landscapeVideoMode", landscapeVideoMode.value)
        put("equalizerEnabled", equalizerEnabled.value); put("equalizerBands", org.json.JSONArray(equalizerBands.value))
    }
    fun restore(json: org.json.JSONObject) {
        setDataSaver(json.optBoolean("dataSaver", false)); setAutoplay(json.optBoolean("autoplay", true))
        setDownloadsEnabled(json.optBoolean("downloads", true)); setWifiOnly(json.optBoolean("wifi", true))
        setPrepareNext(json.optBoolean("prepareNext", false)); setSpokenSpeed(json.optDouble("spokenSpeed", 1.0).toFloat())
        setNormalise(json.optBoolean("normalise", false)); setSkipFailed(json.optBoolean("skipFailed", false))
        setOfflineOnly(json.optBoolean("offlineOnly", false))
        setLandscapeVideoMode(json.optString("landscapeVideoMode", "wifi").takeIf { it in setOf("off", "wifi", "always") } ?: "wifi")
        setEqualizerEnabled(json.optBoolean("equalizerEnabled", false))
        json.optJSONArray("equalizerBands")?.let { a -> setEqualizerBands((0..4).map { a.optInt(it, 0) }) }
    }

    private companion object {
        const val KEY_DATA_SAVER = "data_saver"
        const val KEY_AUTOPLAY = "autoplay"
    }
}
