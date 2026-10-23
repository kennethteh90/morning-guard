package com.morningguard.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MorningGoalConfigTest {

    @Test
    fun `timed focus default duration is two minutes`() {
        val config = MorningGoalConfig(type = GoalType.TIMED_FOCUS)
        assertEquals(2, config.focusMinutes)
    }
}
