package com.morningguard.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalTime

@Serializable
data class MorningWindow(
    val startHour: Int = 1,
    val startMinute: Int = 0,
    val endHour: Int = 10,
    val endMinute: Int = 0,
) {
    fun contains(localTime: LocalTime): Boolean {
        val start = LocalTime.of(startHour, startMinute)
        val end = LocalTime.of(endHour, endMinute)
        return if (start <= end) {
            !localTime.isBefore(start) && !localTime.isAfter(end)
        } else {
            !localTime.isBefore(start) || !localTime.isAfter(end)
        }
    }
}

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
    val bypassPackageNames: Set<String> = DefaultBypassPackages.packageNames,
    val morningGoal: MorningGoalConfig = MorningGoalConfig(type = GoalType.BIBLE_CHAPTER),
    val digestOnlyDuringMorningGate: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val dataStorageMode: DataStorageMode = DataStorageMode.LOCAL_ONLY,
    /** Last Supabase user id that owned local sync data on this device. */
    val syncBoundUserId: String? = null,
    /** Recovery-password onboarding (cloud sync). */
    val recoveryPasswordPromptShownAfterSignIn: Boolean = false,
    val recoveryPasswordLaterCount: Int = 0,
    val recoveryPasswordPromptLastDismissedEpochMs: Long = 0,
    val noteSaveCountForRecoveryPrompt: Int = 0,
)
