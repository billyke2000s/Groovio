package com.groovio.app.ui.library

/**
 * Retained for callers/tests that use the offline banner copy. The old
 * Downloads screen was merged into LocalFilesScreen (Your music); both
 * navigation routes now render the same unified library.
 */
fun offlineMessage(count: Int): String = if (count > 0)
    "Offline · $count saved songs · Open Your music"
else "Offline · Open Your music"
