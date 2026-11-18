package com.groovio.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.groovio.app.BuildConfig
import com.groovio.app.GroovioApplication
import com.groovio.app.update.AvailableUpdate
import com.groovio.app.update.UpdateChecker
import com.groovio.app.update.UpdateInstaller
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

    private val installer = UpdateInstaller(app)
    private val _update = MutableStateFlow<UpdateState?>(null)
    /** In-app update progress, or null when Groovio is up to date. */
    val update: StateFlow<UpdateState?> = _update.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    /** Result of a manual "Check for updates" (e.g. "You're up to date"). */
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()
    private var lastCheck = 0L
    private var downloadJob: kotlinx.coroutines.Job? = null

    private val _chart = MutableStateFlow<com.groovio.app.online.SpotifyCollection?>(null)
    val chart = _chart.asStateFlow()
    private val _mix = MutableStateFlow<RelatedShelf?>(null)
    val mix = _mix.asStateFlow()
    private var loadingDiscover = false
    private val _discover = MutableStateFlow<List<DiscoverShelf>>(emptyList())
    val discover: StateFlow<List<DiscoverShelf>> = _discover.asStateFlow()

    private val mixStore = PersonalMixStore(app)
    private val _personalMixes = MutableStateFlow(mixStore.read(java.time.LocalDate.now().toString()))
    val personalMixes = _personalMixes.asStateFlow()
    private val _mixNotice = MutableStateFlow<String?>(null)
    val mixNotice = _mixNotice.asStateFlow()

    fun saveMix(mix: PersonalMix) {
        viewModelScope.launch {
            runCatching {
                val id = groovio.library.createPlaylist(mix.title)
                mix.tracks.forEach { groovio.library.addToPlaylist(id, com.groovio.app.data.TrackMapping.fromOnline(it)) }
            }.onSuccess { _mixNotice.value = "${mix.title} saved to Your Library" }
             .onFailure { _mixNotice.value = "Couldn't save this mix. Please try again." }
        }
    }

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(groovio.library.favourites, groovio.library.recent(50), groovio.settings.offlineOnly) {
                likes, history, offline -> Triple(likes, history, offline)
            }.collectLatest { (likes, history, offline) ->
                if (likes.isEmpty() && history.isEmpty()) {
                    mixStore.clear(); _personalMixes.value = emptyList(); return@collectLatest
                }
                if (offline) return@collectLatest
                val day = java.time.LocalDate.now().toString()
                val cached = mixStore.read(day)
                if (cached.isNotEmpty()) { _personalMixes.value = cached; return@collectLatest }
                val mixes = withContext(Dispatchers.IO) {
                    MixSelection.seeds(likes, history).mapIndexedNotNull { index, seed ->
                        val tracks = try { groovio.youtube.related(seed.mediaId.removePrefix("yt:")) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { emptyList() }
                        tracks.takeIf { it.isNotEmpty() }?.let {
                            PersonalMix("$day-${seed.mediaId}", "Daily Mix ${index + 1}",
                                "Based on ${seed.artist ?: seed.title}", it)
                        }
                    }.let(MixSelection::diversify)
                }
                if (mixes.isNotEmpty()) { mixStore.write(day, mixes); _personalMixes.value = mixes }
            }
        }
        loadDiscover()
        viewModelScope.launch {
            groovio.library.favourites.collectLatest { favourites ->
                if (favourites.isEmpty()) { _mix.value = null; return@collectLatest }
                if (!groovio.connectivity.online.value || groovio.settings.offlineOnly.value) return@collectLatest
                val seeds = favourites.filter { it.mediaId.startsWith("yt:") }.take(3)
                val results = withContext(Dispatchers.IO) {
                    seeds.flatMap { seed ->
                        runCatching { groovio.youtube.related(seed.mediaId.removePrefix("yt:")) }.getOrDefault(emptyList())
                    }.distinctBy { it.id }.filter { song -> favourites.none { it.mediaId == "yt:${song.id}" } }.take(20)
                }
                _mix.value = results.takeIf { it.isNotEmpty() }?.let { RelatedShelf("your favourites", it) }
            }
        }
        checkForUpdates()
        viewModelScope.launch {
            groovio.library.recent(10)
                .map { list -> list.firstOrNull { it.mediaId.startsWith("yt:") } }
                .distinctUntilChanged { a, b -> a?.mediaId == b?.mediaId }
                .collectLatest { seed ->
                    if (seed == null) { _related.value = null; return@collectLatest }
                    if (groovio.settings.offlineOnly.value || !groovio.connectivity.online.value) return@collectLatest
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
        if (_discover.value.isNotEmpty() || loadingDiscover || groovio.settings.offlineOnly.value || !groovio.connectivity.online.value) return
        loadingDiscover = true
        viewModelScope.launch {
            _chart.value = withContext(Dispatchers.IO) {
                runCatching { com.groovio.app.online.ChartDiscovery().ukChart() }.getOrNull()
            }
            val year = java.time.Year.now().value
            val yt = groovio.youtube
            val specs: List<suspend () -> DiscoverShelf> = listOf(
                { DiscoverShelf("Popular song search", tracks = yt.searchSongs("top hits $year").songLength().take(15)) },
                { DiscoverShelf("Discover albums", albums = yt.searchAlbums("new album $year").take(12)) },
                { DiscoverShelf("Trending song search", tracks = yt.searchSongs("top 50 global $year").songLength().take(20)) },
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

    /** Checks GitHub for a newer release. Automatic checks run at most once a minute. */
    fun checkForUpdates(manual: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!manual && now - lastCheck < 60_000) return
        if (_update.value is UpdateState.Downloading) return
        lastCheck = now
        if (manual) _updateMessage.value = "Checking…"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { UpdateChecker(BuildConfig.VERSION_CODE, experimental = groovio.settings.experimentalUpdates.value).check() }
            }
            result.onSuccess { found ->
                if (found != null) {
                    if (_update.value?.update?.version != found.version) _update.value = UpdateState.Available(found)
                    if (manual) _updateMessage.value = "Groovio ${found.version} is available"
                } else if (manual) {
                    _updateMessage.value = "You're up to date"
                }
            }.onFailure { if (manual) _updateMessage.value = "Couldn't check for updates. Check your connection." }
        }
    }

    /** Called when the app comes back to the foreground. */
    fun onResume() {
        checkForUpdates()
        // Returning from the "Install unknown apps" switch: carry on installing.
        val state = _update.value
        if (state is UpdateState.NeedsPermission && installer.canInstall()) {
            installer.install(state.file)
            _update.value = UpdateState.Ready(state.update, state.file)
        }
    }

    /** Update button: download inside the app, then show Android's install screen. */
    fun startUpdate() {
        val state = _update.value ?: return
        when (state) {
            is UpdateState.Downloading -> return
            is UpdateState.Ready -> { install(state.update, state.file); return }
            is UpdateState.NeedsPermission -> { installer.openInstallPermission(); return }
            else -> Unit
        }
        val update = state.update
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _update.value = UpdateState.Downloading(update, 0f)
            try {
                val file = installer.download(update) { p -> _update.value = UpdateState.Downloading(update, p) }
                install(update, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.value = UpdateState.Failed(update, e.message ?: "Download failed")
            }
        }
    }

    private fun install(update: AvailableUpdate, file: java.io.File) {
        if (installer.canInstall()) {
            _update.value = UpdateState.Ready(update, file)
            installer.install(file)
        } else {
            _update.value = UpdateState.NeedsPermission(update, file)
            installer.openInstallPermission()
        }
    }
}

sealed interface UpdateState {
    val update: AvailableUpdate
    data class Available(override val update: AvailableUpdate) : UpdateState
    data class Downloading(override val update: AvailableUpdate, val progress: Float) : UpdateState
    data class NeedsPermission(override val update: AvailableUpdate, val file: java.io.File) : UpdateState
    data class Ready(override val update: AvailableUpdate, val file: java.io.File) : UpdateState
    data class Failed(override val update: AvailableUpdate, val message: String) : UpdateState
}

/** Drops compilations and hour-long mixes that sometimes appear in song searches. */
private fun List<OnlineTrack>.songLength(): List<OnlineTrack> =
    filter { t -> t.durationMs == null || t.durationMs in 60_000L..600_000L }
