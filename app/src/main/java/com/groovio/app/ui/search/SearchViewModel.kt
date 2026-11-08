package com.groovio.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.groovio.app.GroovioApplication
import com.groovio.app.online.OnlineTrack
import com.groovio.app.online.SpotifyCollection
import com.groovio.app.online.SpotifyImporter
import com.groovio.app.online.SpotifyLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<OnlineTrack> = emptyList(),
    /** Set when the query is a Spotify link that was read successfully. */
    val spotify: SpotifyCollection? = null,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val youtube = (app as GroovioApplication).youtube
    private val spotify: SpotifyImporter = (app as GroovioApplication).spotify
    private val query = MutableStateFlow("")
    private val retryTick = MutableStateFlow(0)

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** What's typed, updated immediately (searching is debounced). */
    val input: StateFlow<String> = query.asStateFlow()

    init {
        val debounced = query
            .map { it.trim() }
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
        viewModelScope.launch {
            combine(debounced, retryTick) { q, _ -> q }.collectLatest { q ->
                if (q.length < MIN_QUERY) {
                    _state.value = SearchState()
                    return@collectLatest
                }
                _state.value = _state.value.copy(query = q, loading = true, error = null)
                val link = SpotifyLinks.findLink(q)
                _state.value = try {
                    if (link != null) {
                        val collection = withContext(Dispatchers.IO) { spotify.load(link) }
                        SearchState(query = q, spotify = collection)
                    } else {
                        val results = withContext(Dispatchers.IO) { youtube.searchSongs(q) }
                        SearchState(query = q, results = results)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SearchState(query = q, error = describe(e))
                }
            }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun retry() {
        retryTick.value++
    }

    private fun describe(e: Exception): String = when (e) {
        is ReCaptchaException, is SignInConfirmNotBotException ->
            "YouTube is blocking this network right now. Try again later or switch network."
        is IOException -> "Couldn't connect. Check your internet and try again."
        is IllegalArgumentException -> e.message ?: "Couldn't read that link."
        else -> "Search failed. Try again."
    }

    private companion object {
        const val DEBOUNCE_MS = 450L
        const val MIN_QUERY = 2
    }
}
