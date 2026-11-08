package com.groovio.app

import android.app.Application
import com.groovio.app.online.NewPipeDownloader
import com.groovio.app.online.YouTubeMusic
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

/** App-wide singletons (manual DI). The playback service runs in this same process. */
class GroovioApplication : Application() {

    val youtube: YouTubeMusic by lazy { YouTubeMusic() }

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
