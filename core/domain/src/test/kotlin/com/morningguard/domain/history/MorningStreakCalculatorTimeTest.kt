package com.morningguard.domain.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MorningStreakCalculatorTimeTest {
    @Test
    fun totalGoalTimeSeconds_mergesMapHistoryAndActiveProgress() {
        val history = listOf(
            MorningDayRecord(
                dateKey = "2026-10-15",
                outcome = MorningDayOutcome.GOAL_COMPLETED,
                timeOnGoalSeconds = 120,
            ),
        )
        val total = MorningStreakCalculator.totalGoalTimeSeconds(
            dailySeconds = mapOf("2026-10-16" to 60),
            history = history,
            activeProgressSeconds = 30,
            activeDateKey = "2026-10-17",
        )
        assertEquals(120 + 60 + 30, total)
    }

    @Test
    fun totalBibleReadingSeconds_sumsDailyMap() {
        val total = MorningStreakCalculator.totalBibleReadingSeconds(
            mapOf("2026-10-15" to 90, "2026-10-16" to 120),
        )
        assertEquals(210, total)
    }
}
