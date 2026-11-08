package com.groovio.app

import android.app.Application
import com.groovio.app.data.LibraryDatabase
import com.groovio.app.data.LibraryRepository
import com.groovio.app.online.NewPipeDownloader
import com.groovio.app.online.SpotifyImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.groovio.app.online.YouTubeMusic
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

/** App-wide singletons (manual DI). The playback service runs in this same process. */
class GroovioApplication : Application() {

    val youtube: YouTubeMusic by lazy { YouTubeMusic() }
    val spotify: SpotifyImporter by lazy { SpotifyImporter() }

    /** Text shared to Groovio (e.g. a Spotify link), waiting for the UI to pick it up. */
    val sharedText = MutableStateFlow<String?>(null)
    val library: LibraryRepository by lazy { LibraryRepository(LibraryDatabase.create(this).dao()) }

    /** Outlives screens and the service, for short writes that must finish (e.g. saving the queue). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val locale = Locale.getDefault()
        NewPipe.init(
            NewPipeDownloader(),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { "GB" }),
        )
    }
}
