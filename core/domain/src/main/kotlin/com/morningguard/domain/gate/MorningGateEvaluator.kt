package com.morningguard.domain.gate

import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalProgress
import com.morningguard.domain.model.MorningWindow
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class GateState {
    INACTIVE,
    LOCKED,
    UNLOCKED,
}

data class GateEvaluation(
    val state: GateState,
    val dateKey: String,
    val reason: String,
)

class MorningGateEvaluator(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    fun evaluate(
        window: MorningWindow,
        progress: MorningGoalProgress?,
        emergencyBypassActiveUntilEpochMs: Long?,
    ): GateEvaluation {
        val nowMs = nowProvider()
        val zonedNow = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val dateKey = zonedNow.toLocalDate().toString()

        if (emergencyBypassActiveUntilEpochMs != null && nowMs < emergencyBypassActiveUntilEpochMs) {
            return GateEvaluation(GateState.UNLOCKED, dateKey, "emergency_bypass")
        }

        if (progress?.dateKey == dateKey && progress.isComplete) {
            return GateEvaluation(GateState.UNLOCKED, dateKey, "goal_complete")
        }

        val nowTime = zonedNow.toLocalTime()
        val start = LocalTime.of(window.startHour, window.startMinute)
        val end = LocalTime.of(window.endHour, window.endMinute)
        val inWindow = if (start <= end) {
            !nowTime.isBefore(start) && !nowTime.isAfter(end)
        } else {
            !nowTime.isBefore(start) || !nowTime.isAfter(end)
        }

        return if (inWindow) {
            GateEvaluation(GateState.LOCKED, dateKey, "morning_window")
        } else {
            GateEvaluation(GateState.INACTIVE, dateKey, "outside_window")
        }
    }
}

class GoalCompletionVerifier(
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    fun bibleChapterComplete(
        progress: MorningGoalProgress,
        minimumReadSeconds: Int,
    ): Boolean {
        val started = progress.startedAtEpochMs ?: return false
        val elapsedSec = (nowProvider() - started) / 1000
        return elapsedSec >= minimumReadSeconds
    }

    fun timedFocusComplete(progress: MorningGoalProgress, requiredMinutes: Int): Boolean {
        val started = progress.startedAtEpochMs ?: return false
        val elapsedMin = (nowProvider() - started) / 60_000
        return elapsedMin >= requiredMinutes
    }

    fun stepCountComplete(progress: MorningGoalProgress, currentSteps: Int, target: Int): Boolean {
        val baseline = progress.stepsAtStart ?: return false
        return (currentSteps - baseline) >= target
    }

    fun canMarkComplete(
        progress: MorningGoalProgress,
        currentSteps: Int?,
    ): Boolean {
        return when (progress.config.type) {
            GoalType.BIBLE_CHAPTER -> bibleChapterComplete(progress, minimumReadSeconds = 60)
            GoalType.TIMED_FOCUS -> timedFocusComplete(progress, progress.config.focusMinutes)
            GoalType.STEP_COUNT -> {
                val steps = currentSteps ?: return false
                stepCountComplete(progress, steps, progress.config.stepTarget)
            }
        }
    }
}
