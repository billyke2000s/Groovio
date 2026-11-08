package com.groovio.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.groovio.app.GroovioApplication
import com.groovio.app.data.PlaylistItem
import com.groovio.app.data.PlaylistSummary
import com.groovio.app.data.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val library = (app as GroovioApplication).library

    val favourites: StateFlow<List<TrackEntity>> = library.favourites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent: StateFlow<List<TrackEntity>> = library.recent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = library.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playlistName(id: Long): Flow<String?> = library.playlistName(id)
    fun playlistItems(id: Long): Flow<List<PlaylistItem>> = library.playlistItems(id)

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { library.createPlaylist(name) }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { library.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { library.deletePlaylist(id) }
    }

    fun removeFromPlaylist(id: Long, position: Int) {
        viewModelScope.launch { library.removeFromPlaylist(id, position) }
    }

    fun unlike(track: TrackEntity) {
        viewModelScope.launch { library.setFavourite(track, false) }
    }
}
