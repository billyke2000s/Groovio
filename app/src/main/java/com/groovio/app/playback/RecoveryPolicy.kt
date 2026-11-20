package com.groovio.app.playback

/** Retry an individual stream once, then skip failures without looping endlessly in repeat mode. */
class RecoveryPolicy {
    private val retries = mutableSetOf<String>()
    private var skipped = 0
    fun transition() { retries.clear() }
    fun started() { skipped = 0 }
    fun retry(id: String, transient: Boolean, userWantsPlay: Boolean): Boolean =
        transient && userWantsPlay && retries.add(id)
    /** At most one queue's worth of consecutive failed items, reset by actual audio playback. */
    fun skip(skippable: Boolean, enabled: Boolean, userWantsPlay: Boolean, hasNext: Boolean, queueSize: Int): Boolean {
        if (!skippable || !enabled || !userWantsPlay || !hasNext || skipped >= queueSize.coerceAtLeast(1)) return false
        skipped++
        return true
    }
}
