package com.morningguard.domain.history

import kotlinx.serialization.Serializable

@Serializable
enum class MorningDayOutcome {
    GOAL_COMPLETED,
    EMERGENCY_BYPASS,
}

@Serializable
data class MorningDayRecord(
    val dateKey: String,
    val outcome: MorningDayOutcome,
    val recordedAtEpochMs: Long = System.currentTimeMillis(),
    /** Foreground seconds on the morning goal screen that day (Bible / focus goals). */
    val timeOnGoalSeconds: Int = 0,
    /** Steps counted toward the step goal that day (when recorded). */
    val stepsOnGoal: Int = 0,
)

object MorningStreakCalculator {
    fun currentCompletedStreak(history: List<MorningDayRecord>, todayKey: String): Int {
        val completedDays = history
            .filter { it.outcome == MorningDayOutcome.GOAL_COMPLETED }
            .map { it.dateKey }
            .toSet()
        var streak = 0
        var day = java.time.LocalDate.parse(todayKey)
        while (completedDays.contains(day.toString())) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun bestCompletedStreak(history: List<MorningDayRecord>): Int {
        val completed = history
            .filter { it.outcome == MorningDayOutcome.GOAL_COMPLETED }
            .map { it.dateKey }
            .toSet()
        if (completed.isEmpty()) return 0
        val sorted = completed.map { java.time.LocalDate.parse(it) }.sorted()
        var best = 0
        var run = 0
        var prev: java.time.LocalDate? = null
        for (day in sorted) {
            run = if (prev != null && day.minusDays(1) == prev) run + 1 else 1
            best = maxOf(best, run)
            prev = day
        }
        return best
    }

    fun countOutcome(history: List<MorningDayRecord>, outcome: MorningDayOutcome): Int =
        history.count { it.outcome == outcome }

    /** Sum of per-day goal-screen time; [dailySeconds] should hold the latest second count per date key. */
    fun totalGoalTimeSeconds(
        dailySeconds: Map<String, Int>,
        history: List<MorningDayRecord>,
        activeProgressSeconds: Int?,
        activeDateKey: String?,
    ): Int {
        val merged = dailySeconds.toMutableMap()
        for (record in history) {
            merged[record.dateKey] = maxOf(merged[record.dateKey] ?: 0, record.timeOnGoalSeconds)
        }
        if (activeDateKey != null && activeProgressSeconds != null) {
            merged[activeDateKey] = maxOf(merged[activeDateKey] ?: 0, activeProgressSeconds)
        }
        return merged.values.sum()
    }

    /** Sum of per-day seconds with the Bible reader open (morning goal or Read Bible). */
    fun totalBibleReadingSeconds(dailySeconds: Map<String, Int>): Int =
        dailySeconds.values.sum()

    fun totalGoalSteps(
        dailySteps: Map<String, Int>,
        history: List<MorningDayRecord>,
        activeProgressSteps: Int?,
        activeDateKey: String?,
    ): Int {
        val merged = dailySteps.toMutableMap()
        for (record in history) {
            merged[record.dateKey] = maxOf(merged[record.dateKey] ?: 0, record.stepsOnGoal)
        }
        if (activeDateKey != null && activeProgressSteps != null) {
            merged[activeDateKey] = maxOf(merged[activeDateKey] ?: 0, activeProgressSteps)
        }
        return merged.values.sum()
    }
}
