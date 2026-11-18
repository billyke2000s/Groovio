package com.groovio.app.playback

import org.junit.Assert.*
import org.junit.Test

class RecoveryPolicyTest {
    @Test fun `paused playback never retries or skips`() {
        val policy = RecoveryPolicy()
        assertFalse(policy.retry("a", true, false))
        assertFalse(policy.skip(true, true, false, true))
        assertTrue(policy.retry("a", true, true))
        assertFalse(policy.retry("a", true, true))
    }
    @Test fun `skip budget survives transitions until audio really starts`() {
        val policy = RecoveryPolicy()
        repeat(3) { assertTrue(policy.skip(true, true, true, true)); policy.transition() }
        assertFalse(policy.skip(true, true, true, true))
        policy.started()
        assertTrue(policy.skip(true, true, true, true))
    }
    @Test fun `temporary network failures are not unavailable songs`() {
        assertFalse(RecoveryPolicy().skip(false, true, true, true))
        assertFalse(RecoveryPolicy().skip(true, false, true, true))
    }
}
