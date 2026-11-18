package com.groovio.app.ui.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hides a particular card from one Home shelf only. This never removes a
 * playlist, a saved track, or anything from the user's actual library.
 */
class HomeCardVisibility(context: Context) {
    private val preferences = context.getSharedPreferences("home_card_visibility", Context.MODE_PRIVATE)
    private val hiddenFlow = MutableStateFlow(preferences.getStringSet("hidden", emptySet()).orEmpty().toSet())
    val hidden = hiddenFlow.asStateFlow()

    @Synchronized fun hide(section: String, id: String) {
        if (section !in setOf("quick", "jump", "mixes", "playlists") || id.isBlank()) return
        val newHidden = hiddenFlow.value + key(section, id)
        if (preferences.edit().putStringSet("hidden", newHidden).commit()) hiddenFlow.value = newHidden
    }

    companion object {
        fun key(section: String, id: String): String = "$section:$id"
        fun visible(hidden: Set<String>, section: String, id: String): Boolean = key(section, id) !in hidden
    }
}
