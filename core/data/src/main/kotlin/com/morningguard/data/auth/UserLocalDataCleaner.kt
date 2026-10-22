package com.morningguard.data.auth

import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserLocalDataCleaner @Inject constructor(
    private val notificationCaptureRepository: NotificationCaptureRepository,
    private val bibleHighlightRepository: BibleHighlightRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun clearOnSignOut() {
        notificationCaptureRepository.clearAll()
        bibleHighlightRepository.clearAll()
        settingsRepository.clearSensitiveGoalData()
        settingsRepository.setBoundUserId(null)
    }
}
