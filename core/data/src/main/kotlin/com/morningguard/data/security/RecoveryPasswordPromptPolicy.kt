package com.morningguard.data.security

import com.morningguard.domain.model.DataStorageMode
import com.morningguard.domain.model.UserSettings

object RecoveryPasswordPromptPolicy {
    private const val RE_PROMPT_AFTER_DISMISS_MS = 7L * 24 * 60 * 60 * 1000
    private val noteMilestones = listOf(1, 3, 5, 10)

    fun shouldShowDedicatedSheet(
        settings: UserSettings,
        hasRecoveryPassword: Boolean,
        signedIn: Boolean,
        cloudRecoveryRequired: Boolean,
    ): Boolean {
        if (!signedIn || settings.dataStorageMode != DataStorageMode.CLOUD_SYNC) return false
        if (hasRecoveryPassword) return false
        if (cloudRecoveryRequired) return true
        return !settings.recoveryPasswordPromptShownAfterSignIn
    }

    fun shouldShowPeriodicSheet(
        settings: UserSettings,
        hasRecoveryPassword: Boolean,
        signedIn: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!signedIn || settings.dataStorageMode != DataStorageMode.CLOUD_SYNC) return false
        if (hasRecoveryPassword) return false
        if (settings.recoveryPasswordPromptShownAfterSignIn &&
            settings.recoveryPasswordLaterCount > 0 &&
            nowEpochMs - settings.recoveryPasswordPromptLastDismissedEpochMs >= RE_PROMPT_AFTER_DISMISS_MS
        ) {
            return true
        }
        return noteMilestones.contains(settings.noteSaveCountForRecoveryPrompt)
    }

    fun shouldShowBanner(
        settings: UserSettings,
        hasRecoveryPassword: Boolean,
        signedIn: Boolean,
    ): Boolean {
        if (!signedIn || settings.dataStorageMode != DataStorageMode.CLOUD_SYNC) return false
        return !hasRecoveryPassword
    }
}
