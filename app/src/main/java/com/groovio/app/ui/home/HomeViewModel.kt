package com.groovio.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.groovio.app.BuildConfig
import com.groovio.app.GroovioApplication
import com.groovio.app.update.AvailableUpdate
import com.groovio.app.update.UpdateChecker
import com.groovio.app.online.OnlineAlbum
import com.groovio.app.online.OnlineTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

/** A discovery shelf that works without any history (charts, new albums, genres). */
data class DiscoverShelf(
    val title: String,
    val tracks: List<OnlineTrack> = emptyList(),
    val albums: List<OnlineAlbum> = emptyList(),
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val groovio = app as GroovioApplication

    private val _related = MutableStateFlow<RelatedShelf?>(null)
    val related: StateFlow<RelatedShelf?> = _related.asStateFlow()

    private val _update = MutableStateFlow<AvailableUpdate?>(null)
    /** A newer Groovio release, if GitHub has one. */
    val update: StateFlow<AvailableUpdate?> = _update.asStateFlow()

    private var loadingDiscover = false
    private val _discover = MutableStateFlow<List<DiscoverShelf>>(emptyList())
    val discover: StateFlow<List<DiscoverShelf>> = _discover.asStateFlow()

    init {
        loadDiscover()
        viewModelScope.launch {
            _update.value = withContext(Dispatchers.IO) {
                runCatching { UpdateChecker(BuildConfig.VERSION_CODE).check() }.getOrNull()
            }
        }
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

    /** Loads all shelves in parallel; a shelf that fails or comes back empty is left out. */
    fun loadDiscover() {
        if (_discover.value.isNotEmpty() || loadingDiscover) return
        loadingDiscover = true
        viewModelScope.launch {
            val year = java.time.Year.now().value
            val yt = groovio.youtube
            val specs: List<suspend () -> DiscoverShelf> = listOf(
                { DiscoverShelf("Today's top hits", tracks = yt.searchSongs("top hits $year").songLength().take(15)) },
                { DiscoverShelf("Popular albums", albums = yt.searchAlbums("top albums $year").take(12)) },
                { DiscoverShelf("Pop", albums = yt.searchAlbums("pop hits").take(12)) },
                { DiscoverShelf("Hip-hop", albums = yt.searchAlbums("hip hop").take(12)) },
                { DiscoverShelf("Dance & electronic", albums = yt.searchAlbums("dance electronic").take(12)) },
                { DiscoverShelf("Rock", albums = yt.searchAlbums("rock classics").take(12)) },
                { DiscoverShelf("Chill", tracks = yt.searchSongs("chill vibes").songLength().take(15)) },
            )
            val shelves = coroutineScope {
                specs.map { spec ->
                    async(Dispatchers.IO) { runCatching { spec() }.getOrNull() }
                }.awaitAll()
            }
            _discover.value = shelves.filterNotNull().filter { it.tracks.isNotEmpty() || it.albums.isNotEmpty() }
            loadingDiscover = false
        }
    }
}

/** Drops compilations and hour-long mixes that sometimes appear in song searches. */
private fun List<OnlineTrack>.songLength(): List<OnlineTrack> =
    filter { t -> t.durationMs == null || t.durationMs in 60_000L..600_000L }
