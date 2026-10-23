package com.morningguard.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GoalType {
    BIBLE_CHAPTER,
    TIMED_FOCUS,
    STEP_COUNT,
}

@Serializable
data class MorningGoalConfig(
    val type: GoalType,
    val bibleTranslation: String = "niv",
    val bibleBook: String = "John",
    val bibleChapter: Int = 1,
    val focusMinutes: Int = 5,
    val stepTarget: Int = 500,
)

@Serializable
data class MorningGoalProgress(
    val dateKey: String,
    val config: MorningGoalConfig,
    val startedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null,
    val stepsAtStart: Int? = null,
    /** Steps counted toward today's goal (detector pulses while goal screen is open, or counter delta fallback). */
    val stepsAccumulated: Int = 0,
    /** Seconds with the goal screen in the foreground (Bible chapter & timed focus). */
    val onScreenSeconds: Int = 0,
) {
    val isComplete: Boolean get() = completedAtEpochMs != null
}
