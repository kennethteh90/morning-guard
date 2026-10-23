package com.morningguard.domain.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MorningGoalTimeFormatTest {
    @Test
    fun format_seconds_and_minutes() {
        assertEquals("0m", MorningGoalTimeFormat.format(0))
        assertEquals("45s", MorningGoalTimeFormat.format(45))
        assertEquals("5m", MorningGoalTimeFormat.format(300))
        assertEquals("5m 30s", MorningGoalTimeFormat.format(330))
    }

    @Test
    fun format_hours() {
        assertEquals("1h", MorningGoalTimeFormat.format(3600))
        assertEquals("1h 15m", MorningGoalTimeFormat.format(4500))
    }
}
