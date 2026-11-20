package com.groovio.app.playback

import org.junit.Assert.*
import org.junit.Test

class RecoveryPolicyTest {
    @Test fun `paused playback never retries or skips`() {
        val policy = RecoveryPolicy()
        assertFalse(policy.retry("a", true, false))
        assertFalse(policy.skip(true, true, false, true, 5))
        assertTrue(policy.retry("a", true, true))
        assertFalse(policy.retry("a", true, true))
    }
    @Test fun `skip traverses five consecutive failures but cannot loop forever`() {
        val policy = RecoveryPolicy()
        repeat(5) { assertTrue(policy.skip(true, true, true, true, 5)); policy.transition() }
        assertFalse(policy.skip(true, true, true, true, 5))
        policy.started()
        assertTrue(policy.skip(true, true, true, true, 5))
    }
    @Test fun `temporary network failures are not unavailable songs`() {
        assertFalse(RecoveryPolicy().skip(false, true, true, true, 5))
        assertFalse(RecoveryPolicy().skip(true, false, true, true, 5))
    }
}
