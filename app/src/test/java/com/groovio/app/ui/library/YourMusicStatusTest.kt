package com.groovio.app.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A failed public-folder export must not be reported as a failed audio download. */
class YourMusicStatusTest {
    @Test fun completedLocalAudioHidesLegacyFailedWorkerWithMediaId() {
        assertTrue(downloadJobAlreadySaved(
            setOf("audio-download", "yt:abc123", "title:My song"),
            setOf("yt:abc123"), listOf("My song")))
    }

    @Test fun completedLocalAudioHidesOldTitleOnlyMatchWorker() {
        assertTrue(downloadJobAlreadySaved(
            setOf("audio-download", "title:Cold - Acoustic"),
            setOf("yt:unrelated"), listOf("COLD - ACOUSTIC")))
    }

    @Test fun trulyMissingDownloadRemainsVisible() {
        assertFalse(downloadJobAlreadySaved(
            setOf("audio-download", "yt:missing", "title:Missing"),
            setOf("yt:available"), listOf("Available")))
    }

    @Test fun unrelatedJobIsNotTreatedAsSavedAudio() {
        assertFalse(downloadJobAlreadySaved(
            setOf("audio-download"), setOf("yt:available"), listOf("Available")))
    }
}
