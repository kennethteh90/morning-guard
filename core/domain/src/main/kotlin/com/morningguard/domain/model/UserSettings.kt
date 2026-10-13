package com.morningguard.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MorningWindow(
    val startHour: Int = 5,
    val startMinute: Int = 0,
    val endHour: Int = 10,
    val endMinute: Int = 0,
)

@Serializable
data class AlarmSettings(
    val enabled: Boolean = false,
    val hour: Int = 6,
    val minute: Int = 30,
)

@Serializable
data class UserSettings(
    val morningWindow: MorningWindow = MorningWindow(),
    val alarm: AlarmSettings = AlarmSettings(),
    val bypassPackageNames: Set<String> = emptySet(),
    val morningGoal: MorningGoalConfig = MorningGoalConfig(type = GoalType.BIBLE_CHAPTER),
    val digestOnlyDuringMorningGate: Boolean = true,
)
