package com.morningguard.domain.gate

import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningGoalProgress
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoalCompletionVerifierTest {

    private val verifier = GoalCompletionVerifier()

    @Test
    fun `timed focus requires on-screen seconds not wall clock`() {
        val progress = MorningGoalProgress(
            dateKey = "2026-10-13",
            config = MorningGoalConfig(type = GoalType.TIMED_FOCUS, focusMinutes = 5),
            startedAtEpochMs = 0L,
            onScreenSeconds = 4 * 60 + 59,
        )
        assertFalse(verifier.canMarkComplete(progress, progress.config, null))
        val done = progress.copy(onScreenSeconds = 5 * 60)
        assertTrue(verifier.canMarkComplete(done, done.config, null))
    }

    @Test
    fun `bible chapter requires on-screen seconds`() {
        val progress = MorningGoalProgress(
            dateKey = "2026-10-13",
            config = MorningGoalConfig(type = GoalType.BIBLE_CHAPTER),
            onScreenSeconds = 59,
        )
        assertFalse(verifier.canMarkComplete(progress, progress.config, null))
        assertTrue(verifier.canMarkComplete(progress.copy(onScreenSeconds = 60), progress.config, null))
    }

    @Test
    fun `step count uses accumulated steps on progress`() {
        val progress = MorningGoalProgress(
            dateKey = "2026-10-13",
            config = MorningGoalConfig(type = GoalType.STEP_COUNT, stepTarget = 500),
            stepsAccumulated = 499,
        )
        assertFalse(verifier.canMarkComplete(progress, progress.config, null))
        assertTrue(
            verifier.canMarkComplete(
                progress.copy(stepsAccumulated = 500),
                progress.config,
                null,
            ),
        )
    }
}
