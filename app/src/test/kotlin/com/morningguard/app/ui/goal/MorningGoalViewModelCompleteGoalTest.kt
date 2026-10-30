package com.morningguard.app.ui.goal

import com.morningguard.app.sensors.HealthConnectStepReader
import com.morningguard.app.sensors.StepCounterProvider
import com.morningguard.app.sync.UserDataSyncReport
import com.morningguard.app.sync.UserDataSyncService
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.bible.BibleHighlightColorStore
import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.data.bible.BibleRepository
import com.morningguard.data.gate.MorningGateRepository
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.data.settings.GoalProgressStore
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.model.DigestSummary
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.UserSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MorningGoalViewModelCompleteGoalTest {

    private val testDispatcher = StandardTestDispatcher()

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val morningGateRepository: MorningGateRepository = mockk(relaxed = true)
    private val bibleRepository: BibleRepository = mockk(relaxed = true)
    private val stepCounterProvider: StepCounterProvider = mockk(relaxed = true)
    private val healthConnectStepReader: HealthConnectStepReader = mockk(relaxed = true)
    private val analyticsTracker: AnalyticsTracker = mockk(relaxed = true)
    private val notificationCaptureRepository: NotificationCaptureRepository = mockk(relaxed = true)
    private val bibleHighlightRepository: BibleHighlightRepository = mockk(relaxed = true)
    private val bibleHighlightColorStore: BibleHighlightColorStore = mockk(relaxed = true)
    private val userDataSyncService: UserDataSyncService = mockk(relaxed = true)

    private val userSettings = UserSettings(
        morningGoal = MorningGoalConfig(
            type = GoalType.BIBLE_CHAPTER,
            bibleBook = "John",
            bibleChapter = 3,
            bibleTranslation = "nkjv",
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.settings } returns MutableStateFlow(userSettings)
        every { settingsRepository.goalProgressStore } returns MutableStateFlow(GoalProgressStore())
        coEvery { settingsRepository.currentSettings() } returns userSettings
        every { bibleHighlightRepository.highlights } returns MutableStateFlow(emptyList())
        every { bibleHighlightColorStore.colorKey } returns MutableStateFlow("yellow")
        every { stepCounterProvider.isAvailable } returns false
        every { healthConnectStepReader.isAvailable() } returns false
        every { stepCounterProvider.hasStepCounter() } returns false
        every { stepCounterProvider.stepCounter() } returns flowOf(0)
        coEvery { morningGateRepository.completeGoal(any()) } returns Result.success(Unit)
        coEvery { notificationCaptureRepository.snapshotDigestSummary() } returns DigestSummary(
            count = 7,
            latestTitle = "t",
            latestText = "x",
            latestPackage = "com.example",
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): MorningGoalViewModel = MorningGoalViewModel(
        settingsRepository = settingsRepository,
        morningGateRepository = morningGateRepository,
        bibleRepository = bibleRepository,
        stepCounterProvider = stepCounterProvider,
        healthConnectStepReader = healthConnectStepReader,
        analyticsTracker = analyticsTracker,
        notificationCaptureRepository = notificationCaptureRepository,
        bibleHighlightRepository = bibleHighlightRepository,
        bibleHighlightColorStore = bibleHighlightColorStore,
        userDataSyncService = userDataSyncService,
    )

    @Test
    fun `completeGoal emits held count without waiting for sync`() = runTest {
        var syncCompleted = false
        coEvery { userDataSyncService.syncMorningHistoryIfSignedIn() } coAnswers {
            delay(60_000)
            syncCompleted = true
            Result.success(UserDataSyncReport(emptyList()))
        }

        val viewModel = viewModel()
        val heldCounts = mutableListOf<Int>()
        val collectJob = launch { viewModel.goalCompleted.collect { heldCounts.add(it) } }

        viewModel.completeGoal()
        testScheduler.runCurrent()

        assertEquals(listOf(7), heldCounts)
        assertTrue(!syncCompleted)

        collectJob.cancel()
        advanceUntilIdle()
    }
}
