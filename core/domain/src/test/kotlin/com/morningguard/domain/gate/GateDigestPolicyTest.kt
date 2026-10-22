package com.morningguard.domain.gate

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GateDigestPolicyTest {

    @Test
    fun `digest viewable during peace break`() {
        val eval = GateEvaluation(GateState.LOCKED, "2026-10-13", "peace_session")
        assertTrue(GateDigestPolicy.canViewDigest(eval))
        assertFalse(GateDigestPolicy.blockedByMorningGoal(eval))
    }

    @Test
    fun `digest blocked during morning gate`() {
        val eval = GateEvaluation(GateState.LOCKED, "2026-10-13", "morning_window")
        assertFalse(GateDigestPolicy.canViewDigest(eval))
        assertTrue(GateDigestPolicy.blockedByMorningGoal(eval))
    }
}
