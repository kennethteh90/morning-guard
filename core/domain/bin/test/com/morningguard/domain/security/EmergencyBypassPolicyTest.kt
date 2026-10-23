package com.morningguard.domain.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmergencyBypassPolicyTest {

    @Test
    fun `fifth failed attempt sets lockout`() {
        val policy = EmergencyBypassPolicy(nowProvider = { 1_000L })
        var attempts = 0
        var lockedUntil: Long? = null

        repeat(4) {
            val (nextAttempts, nextLocked) = policy.recordFailure(attempts, lockedUntil)
            attempts = nextAttempts
            lockedUntil = nextLocked
            assertEquals(null, lockedUntil)
        }

        val (fifthAttempts, fifthLocked) = policy.recordFailure(attempts, lockedUntil)
        assertEquals(5, fifthAttempts)
        assertEquals(1_000L + EmergencyBypassPolicy.LOCKOUT_DURATION_MS, fifthLocked)
        assertTrue(policy.isLocked(fifthLocked, 1_000L))
    }

    @Test
    fun `lock remains active until duration elapses`() {
        val lockStart = 10_000L
        val lockedUntil = lockStart + EmergencyBypassPolicy.LOCKOUT_DURATION_MS
        val policy = EmergencyBypassPolicy(nowProvider = { lockStart + 60_000L })

        assertTrue(policy.isLocked(lockedUntil, lockStart + 60_000L))
        assertFalse(policy.isLocked(lockedUntil, lockedUntil))
        assertFalse(policy.isLocked(lockedUntil, lockedUntil + 1L))
    }
}
