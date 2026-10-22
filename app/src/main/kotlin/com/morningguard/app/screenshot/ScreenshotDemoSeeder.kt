package com.morningguard.app.screenshot

import com.morningguard.app.debug.DebugWindowPresets
import com.morningguard.app.ui.onboarding.OnboardingStep
import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.data.settings.GoalProgressStore
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.history.MorningDayOutcome
import com.morningguard.domain.model.CapturedNotification
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningGoalProgress
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotDemoSeeder @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationCaptureRepository: NotificationCaptureRepository,
    private val bibleHighlightRepository: BibleHighlightRepository,
) {
    /** Resets to onboarding notification-access step for policy demo video / captures. */
    suspend fun seedOnboardingNotificationDemo() {
        ScreenshotOnboardingPrefs.clear()
        ScreenshotOnboardingPrefs.initialStep = OnboardingStep.NotificationAccess
        ScreenshotOnboardingPrefs.disclosureAccepted = true
        notificationCaptureRepository.clearAll()
        settingsRepository.updateSettings { settings ->
            settings.copy(
                onboardingCompleted = false,
                morningWindow = DebugWindowPresets.includingNow(),
                morningGoal = MorningGoalConfig(type = GoalType.BIBLE_CHAPTER),
            )
        }
    }

    suspend fun seedForStoreCapture() {
        ScreenshotOnboardingPrefs.clear()
        notificationCaptureRepository.clearAll()
        val now = System.currentTimeMillis()
        demoNotifications(now).forEach { notificationCaptureRepository.capture(it) }

        val today = LocalDate.now().toString()
        val window = DebugWindowPresets.includingNow()
        settingsRepository.updateSettings { settings ->
            settings.copy(
                morningWindow = window,
                morningGoal = MorningGoalConfig(
                    type = GoalType.BIBLE_CHAPTER,
                    bibleTranslation = "web",
                    bibleBook = "Psalm",
                    bibleChapter = 23,
                ),
                onboardingCompleted = true,
                digestOnlyDuringMorningGate = true,
            )
        }
        settingsRepository.updateGoalProgress { store ->
            val history = (-6..0).map { offset ->
                val date = LocalDate.now().plusDays(offset.toLong()).toString()
                val outcome = when {
                    offset == -2 -> MorningDayOutcome.EMERGENCY_BYPASS
                    offset % 2 == 0 -> MorningDayOutcome.GOAL_COMPLETED
                    else -> MorningDayOutcome.GOAL_COMPLETED
                }
                com.morningguard.domain.history.MorningDayRecord(
                    dateKey = date,
                    outcome = outcome,
                    recordedAtEpochMs = now + offset,
                    timeOnGoalSeconds = if (outcome == MorningDayOutcome.GOAL_COMPLETED) 420 else 0,
                )
            }
            store.copy(
                progress = MorningGoalProgress(
                    dateKey = today,
                    config = MorningGoalConfig(type = GoalType.BIBLE_CHAPTER, bibleBook = "Psalm", bibleChapter = 23),
                    startedAtEpochMs = now - 60_000,
                ),
                morningHistory = history,
                peaceSessionUntilEpochMs = null,
            )
        }
    }

    suspend fun markTodayGoalComplete() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        settingsRepository.updateGoalProgress { store ->
            val progress = store.progress
            val config = progress?.config
                ?: settingsRepository.currentSettings().morningGoal
            store.copy(
                progress = MorningGoalProgress(
                    dateKey = today,
                    config = config,
                    startedAtEpochMs = progress?.startedAtEpochMs ?: (now - 120_000),
                    completedAtEpochMs = now,
                ),
            )
        }
    }

    suspend fun seedPeaceBreakActive() {
        seedForStoreCapture()
        markTodayGoalComplete()
        val until = System.currentTimeMillis() + 30 * 60_000
        settingsRepository.updateGoalProgress { it.copy(peaceSessionUntilEpochMs = until) }
    }

    suspend fun seedDigestUnlocked() {
        seedForStoreCapture()
        markTodayGoalComplete()
    }

    suspend fun seedSampleHighlights() {
        seedForStoreCapture()
        bibleHighlightRepository.clearAll()
        val now = System.currentTimeMillis()
        bibleHighlightRepository.save(
            BibleHighlight(
                translationId = "niv",
                book = "Psalm",
                chapter = 23,
                label = "Psalm 23 (NIV)",
                excerpt = "The Lord is my shepherd, I lack nothing.",
                verse = 1,
                verseEnd = 1,
                verseNumbers = "1",
                colorKey = "yellow",
                note = "Demo highlight",
                createdAtEpochMs = now,
            ),
        )
    }

    suspend fun seedGateLockedHome() {
        seedForStoreCapture()
    }

    suspend fun resetDemoData() {
        ScreenshotOnboardingPrefs.clear()
        notificationCaptureRepository.clearAll()
        bibleHighlightRepository.clearAll()
        settingsRepository.updateSettings { settings ->
            settings.copy(
                onboardingCompleted = true,
                morningWindow = DebugWindowPresets.includingNow(),
            )
        }
        settingsRepository.updateGoalProgress { GoalProgressStore() }
    }

    private fun demoNotifications(now: Long): List<CapturedNotification> = listOf(
        CapturedNotification(
            id = 0,
            packageName = "com.android.messaging",
            title = "Alex",
            text = "Coffee after your walk?",
            postedAtEpochMs = now - 120_000,
            notificationKey = "screenshot-demo-1",
        ),
        CapturedNotification(
            id = 0,
            packageName = "com.instagram.android",
            title = "Instagram",
            text = "3 new posts from people you follow",
            postedAtEpochMs = now - 90_000,
            notificationKey = "screenshot-demo-2",
        ),
        CapturedNotification(
            id = 0,
            packageName = "com.google.android.gm",
            title = "Team standup",
            text = "Notes from yesterday — no rush",
            postedAtEpochMs = now - 45_000,
            notificationKey = "screenshot-demo-3",
        ),
    )
}
