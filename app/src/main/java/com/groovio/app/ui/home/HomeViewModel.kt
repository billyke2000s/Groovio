package com.groovio.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.groovio.app.GroovioApplication
import com.groovio.app.online.OnlineTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "More like <song>": YouTube's related songs for the most recent online play. */
data class RelatedShelf(val seedTitle: String, val tracks: List<OnlineTrack>)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val groovio = app as GroovioApplication

    private val _related = MutableStateFlow<RelatedShelf?>(null)
    val related: StateFlow<RelatedShelf?> = _related.asStateFlow()

    init {
        viewModelScope.launch {
            groovio.library.recent(10)
                .map { list -> list.firstOrNull { it.mediaId.startsWith("yt:") } }
                .distinctUntilChanged { a, b -> a?.mediaId == b?.mediaId }
                .collectLatest { seed ->
                    if (seed == null) return@collectLatest
                    val tracks = try {
                        withContext(Dispatchers.IO) { groovio.youtube.related(seed.mediaId.removePrefix("yt:")) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Recommendations are optional; keep the previous shelf on failure.
                        return@collectLatest
                    }
                    if (tracks.isNotEmpty()) _related.value = RelatedShelf(seed.title, tracks)
                }
        }
    }
}
