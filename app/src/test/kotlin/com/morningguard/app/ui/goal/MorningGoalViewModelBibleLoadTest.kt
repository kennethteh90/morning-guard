package com.morningguard.app.ui.goal

import com.morningguard.app.sensors.HealthConnectStepReader
import com.morningguard.app.sensors.StepCounterProvider
import com.morningguard.app.sync.UserDataSyncService
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.bible.BibleHighlightColorStore
import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.data.bible.BibleRepository
import com.morningguard.data.gate.MorningGateRepository
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.data.settings.GoalProgressStore
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.bible.BibleChapterContent
import com.morningguard.domain.bible.BiblePassageBlock
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.UserSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MorningGoalViewModelBibleLoadTest {

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
        every { bibleHighlightRepository.highlights } returns flowOf(emptyList())
        every { bibleHighlightColorStore.colorKey } returns MutableStateFlow("yellow")
        every { morningGateRepository.canComplete(any(), any(), any()) } returns false
        every { stepCounterProvider.isAvailable } returns false
        every { healthConnectStepReader.isAvailable() } returns false
        every { stepCounterProvider.hasStepCounter() } returns false
        every { stepCounterProvider.stepCounter() } returns flowOf(0)
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
    fun `failed chapter load clears passage and sets bible load error`() = runTest {
        coEvery {
            bibleRepository.loadChapter("nkjv", "John", 3)
        } returns Result.failure(IllegalStateException("Licensed proxy unavailable"))

        val viewModel = viewModel()
        viewModel.setBrowseOnly(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Licensed proxy unavailable", state.bibleLoadError)
        assertTrue(state.biblePassage.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `successful chapter load clears bible load error`() = runTest {
        val blocks = listOf(BiblePassageBlock.Paragraph("1. In the beginning"))
        coEvery {
            bibleRepository.loadChapter("nkjv", "John", 3)
        } returnsMany listOf(
            Result.failure(IllegalStateException("temporary")),
            Result.success(BibleChapterContent("John 3", blocks)),
        )

        val viewModel = viewModel()
        viewModel.setBrowseOnly(true)
        advanceUntilIdle()
        assertEquals("temporary", viewModel.uiState.value.bibleLoadError)

        viewModel.retryBibleChapterLoad()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.bibleLoadError)
        assertEquals(blocks, state.biblePassage)
    }
}
