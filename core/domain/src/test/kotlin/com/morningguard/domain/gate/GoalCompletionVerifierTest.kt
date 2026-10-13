package com.morningguard.domain.gate

import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningGoalProgress
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoalCompletionVerifierTest {

    @Test
    fun `timed focus requires elapsed minutes`() {
        val verifier = GoalCompletionVerifier { 100_000L }
        val progress = MorningGoalProgress(
            dateKey = "2026-10-13",
            config = MorningGoalConfig(type = GoalType.TIMED_FOCUS, focusMinutes = 5),
            startedAtEpochMs = 0L,
        )
        assertFalse(verifier.canMarkComplete(progress, null))
        val done = GoalCompletionVerifier { 6 * 60_000L }
        assertTrue(done.canMarkComplete(progress, null))
    }
}
