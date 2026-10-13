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
    val bibleTranslation: String = "web",
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
) {
    val isComplete: Boolean get() = completedAtEpochMs != null
}
