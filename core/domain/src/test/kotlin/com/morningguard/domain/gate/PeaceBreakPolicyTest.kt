package com.morningguard.domain.gate

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeaceBreakPolicyTest {

    @Test
    fun `peace break hidden while morning goal active`() {
        val gate = GateEvaluation(GateState.LOCKED, "2026-10-23", "morning_window")
        assertFalse(PeaceBreakPolicy.canOfferPeaceBreak(gate, peaceSessionActive = false))
    }

    @Test
    fun `peace break offered outside morning window on fresh install`() {
        val gate = GateEvaluation(GateState.INACTIVE, "2026-10-23", "outside_window")
        assertTrue(PeaceBreakPolicy.canOfferPeaceBreak(gate, peaceSessionActive = false))
    }

    @Test
    fun `peace break offered after goal unlock`() {
        val gate = GateEvaluation(GateState.UNLOCKED, "2026-10-23", "goal_complete")
        assertTrue(PeaceBreakPolicy.canOfferPeaceBreak(gate, peaceSessionActive = false))
    }
}
