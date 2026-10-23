package com.morningguard.domain.gate

/** When the user may start or see "Start peace break" on Home. */
object PeaceBreakPolicy {
    fun isMorningGoalActive(gate: GateEvaluation): Boolean =
        gate.state == GateState.LOCKED && gate.reason == "morning_window"

    fun canOfferPeaceBreak(gate: GateEvaluation, peaceSessionActive: Boolean): Boolean =
        !peaceSessionActive && !isMorningGoalActive(gate)
}
