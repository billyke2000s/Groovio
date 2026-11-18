package com.groovio.app.playback

/** A temporary failure gets one retry; skipping cannot run away through a whole queue. */
class RecoveryPolicy {
    private val retries = mutableSetOf<String>()
    private var skipped = 0
    fun transition() { retries.clear() }
    fun started() { skipped = 0 }
    fun retry(id: String, transient: Boolean, userWantsPlay: Boolean): Boolean =
        transient && userWantsPlay && retries.add(id)
    fun skip(unavailable: Boolean, enabled: Boolean, userWantsPlay: Boolean, hasNext: Boolean): Boolean {
        if (!unavailable || !enabled || !userWantsPlay || !hasNext || skipped >= 3) return false
        skipped++; return true
    }
}
