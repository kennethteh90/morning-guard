package com.morningguard.domain.gate

/** Digest viewing is independent of peace break; morning gate still requires goal completion. */
object GateDigestPolicy {
    fun canViewDigest(evaluation: GateEvaluation): Boolean {
        if (evaluation.reason == "peace_session") return true
        return evaluation.state != GateState.LOCKED
    }

    fun blockedByMorningGoal(evaluation: GateEvaluation): Boolean {
        return evaluation.state == GateState.LOCKED && evaluation.reason == "morning_window"
    }
}
