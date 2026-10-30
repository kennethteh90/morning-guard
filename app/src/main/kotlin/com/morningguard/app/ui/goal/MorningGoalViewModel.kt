package com.morningguard.app.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.platform.AppDebugFlags
import com.morningguard.app.di.ApplicationScope
import com.morningguard.app.sensors.HealthConnectStepReader
import com.morningguard.app.sensors.StepCounterProvider
import java.time.Instant
import java.time.LocalDate
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.app.sync.UserDataSyncService
import com.morningguard.data.bible.BibleHighlightColorStore
import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.data.bible.BibleRepository
import com.morningguard.data.gate.MorningGateRepository
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.bible.BibleCanon
import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.bible.BibleHighlightColors
import com.morningguard.domain.bible.BibleTranslations
import com.morningguard.domain.bible.BibleVerseCopyFormat
import com.morningguard.domain.bible.BibleVerseLine
import com.morningguard.domain.bible.BibleVerseReferenceFormat
import com.morningguard.domain.bible.BiblePassageBlock
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningGoalProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MorningGoalUiState(
    val config: MorningGoalConfig = MorningGoalConfig(type = GoalType.BIBLE_CHAPTER),
    val progress: MorningGoalProgress? = null,
    val biblePassage: List<BiblePassageBlock> = emptyList(),
    val loadingBible: Boolean = false,
    /** Shown at the top of the Bible reader when chapter load fails. */
    val bibleLoadError: String? = null,
    /** Goal completion or other non-Bible errors (shown below the goal body). */
    val error: String? = null,
    val currentSteps: Int? = null,
    val stepHardwareAvailable: Boolean = false,
    val stepDebug: StepGoalDebugInfo? = null,
    val healthConnectNeedsPermission: Boolean = false,
    val canComplete: Boolean = false,
    val bibleChapterHighlights: List<BibleHighlight> = emptyList(),
    val lastHighlightColorKey: String = BibleHighlightColors.DEFAULT_KEY,
)

@HiltViewModel
class MorningGoalViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val morningGateRepository: MorningGateRepository,
    private val bibleRepository: BibleRepository,
    private val stepCounterProvider: StepCounterProvider,
    private val healthConnectStepReader: HealthConnectStepReader,
    private val analyticsTracker: AnalyticsTracker,
    private val notificationCaptureRepository: NotificationCaptureRepository,
    private val bibleHighlightRepository: BibleHighlightRepository,
    private val bibleHighlightColorStore: BibleHighlightColorStore,
    private val userDataSyncService: UserDataSyncService,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _goalCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val goalCompleted: SharedFlow<Int> = _goalCompleted.asSharedFlow()

    private val _highlightSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val highlightSaved: SharedFlow<Unit> = _highlightSaved.asSharedFlow()

    private val biblePassage = MutableStateFlow<List<BiblePassageBlock>>(emptyList())
    private val loadingBible = MutableStateFlow(false)
    private val bibleLoadError = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val currentSteps = MutableStateFlow<Int?>(null)
    private val goalScreenForeground = MutableStateFlow(false)
    private val browseOnly = MutableStateFlow(false)
    private val stepDebug = MutableStateFlow(StepGoalDebugInfo())
    private val healthConnectNeedsPermission = MutableStateFlow(false)
    private var loadedBibleChapterKey: String? = null
    private var biblePrefetchJob: Job? = null

    val uiState: StateFlow<MorningGoalUiState> = combine(
        combine(
            combine(settingsRepository.settings, settingsRepository.goalProgressStore) { settings, store ->
                settings to store
            },
            combine(biblePassage, loadingBible, bibleLoadError, currentSteps, browseOnly) { passage, loading, bibleErr, steps, browsing ->
                PassageQuad(passage, loading, bibleErr, steps, browsing)
            },
            stepDebug,
            healthConnectNeedsPermission,
            bibleHighlightRepository.highlights,
        ) { (settings, store), passageQuad, debug, needsHealthConnect, allHighlights ->
            UiCombine(settings, store, passageQuad, debug, needsHealthConnect, allHighlights)
        },
        bibleHighlightColorStore.colorKey,
        error,
    ) { combined, lastColorKey, goalError ->
        val settings = combined.settings
        val store = combined.store
        val passageQuad = combined.passageQuad
        val debug = combined.debug
        val needsHealthConnect = combined.needsHealthConnect
        val allHighlights = combined.allHighlights
        val browsing = passageQuad.browsing
        val quad = passageQuad
        val progress = store.progress
        val goal = settings.morningGoal
        val chapterHighlights = allHighlights.filter { highlight ->
            highlight.translationId == goal.bibleTranslation &&
                highlight.book == goal.bibleBook &&
                highlight.chapter == goal.bibleChapter
        }
        MorningGoalUiState(
            config = goal,
            progress = progress,
            biblePassage = quad.passage,
            loadingBible = quad.loading,
            bibleLoadError = quad.error,
            error = goalError,
            currentSteps = quad.steps,
            stepHardwareAvailable = stepCounterProvider.isAvailable || healthConnectStepReader.isAvailable(),
            healthConnectNeedsPermission = needsHealthConnect,
            stepDebug = if (AppDebugFlags.showDebugPanels) {
                debug.copy(
                    stepsAtStart = progress?.stepsAtStart,
                    stepsAccumulated = progress?.stepsAccumulated ?: 0,
                )
            } else {
                null
            },
            canComplete = !browsing && morningGateRepository.canComplete(
                settings.morningGoal,
                progress,
                quad.steps,
            ),
            bibleChapterHighlights = chapterHighlights,
            lastHighlightColorKey = lastColorKey,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MorningGoalUiState())

    fun setLastHighlightColorKey(colorKey: String) {
        bibleHighlightColorStore.set(colorKey)
    }

    private data class PassageQuad<A, B, C, D>(
        val passage: A,
        val loading: B,
        val error: C,
        val steps: D,
        val browsing: Boolean,
    )

    private data class UiCombine<S, G, P, D, H, L>(
        val settings: S,
        val store: G,
        val passageQuad: P,
        val debug: D,
        val needsHealthConnect: H,
        val allHighlights: L,
    )

    fun setBrowseOnly(enabled: Boolean) {
        browseOnly.value = enabled
        if (enabled) {
            viewModelScope.launch { loadBibleIfNeeded() }
        }
    }

    private var stepCounterCollecting = false
    private var stepDetectionJob: Job? = null

    init {
        ensureStepCounterCollecting()
        viewModelScope.launch {
            if (!browseOnly.value) {
                val settings = settingsRepository.currentSettings()
                if (settings.morningGoal.type != GoalType.STEP_COUNT) {
                    morningGateRepository.startGoalIfNeeded()
                }
            }
            loadBibleIfNeeded()
        }
        viewModelScope.launch {
            goalScreenForeground.collect { foreground ->
                if (foreground) {
                    runOnScreenTicker()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings
                .map { settings ->
                    Triple(
                        settings.morningGoal.bibleBook,
                        settings.morningGoal.bibleChapter,
                        settings.morningGoal.bibleTranslation,
                    )
                }
                .distinctUntilChanged()
                .collect {
                    val type = settingsRepository.currentSettings().morningGoal.type
                    if (type == GoalType.BIBLE_CHAPTER) {
                        loadBibleIfNeeded()
                    }
                }
        }
    }

    fun setGoalScreenForeground(foreground: Boolean) {
        goalScreenForeground.value = foreground
        if (browseOnly.value) {
            if (foreground) {
                viewModelScope.launch { loadBibleIfNeeded() }
            }
            return
        }
        patchStepDebug { it.copy(screenForeground = foreground) }
        if (foreground) {
            refreshStepHardware()
            refreshHealthConnectAccess()
            viewModelScope.launch {
                morningGateRepository.startGoalIfNeeded()
            }
            startStepGoalTracking()
        } else {
            stepDetectionJob?.cancel()
            stepDetectionJob = null
        }
    }

    /** Call after activity recognition permission is granted (Settings or onboarding). */
    fun refreshStepHardware() {
        publishStepSensorSnapshot(counterListening = stepCounterCollecting)
        if (!stepCounterProvider.isAvailable) {
            stepCounterCollecting = false
            patchStepDebug { it.copy(counterListening = false) }
            return
        }
        ensureStepCounterCollecting()
    }

    private fun ensureStepCounterCollecting() {
        if (stepCounterCollecting || !stepCounterProvider.isAvailable) return
        stepCounterCollecting = true
        patchStepDebug { it.copy(counterListening = true, lastEvent = stepDebugTimestamp("counter listen start")) }
        viewModelScope.launch {
            if (!stepCounterProvider.hasStepCounter()) {
                patchStepDebug { it.copy(lastEvent = stepDebugTimestamp("no TYPE_STEP_COUNTER")) }
                return@launch
            }
            stepCounterProvider.stepCounter().collect { steps ->
                currentSteps.value = steps
                patchStepDebug {
                    it.copy(
                        counterTotal = steps,
                        lastEvent = stepDebugTimestamp("counter $steps"),
                    )
                }
            }
        }
    }

    fun healthConnectReadPermissions(): Set<String> = healthConnectStepReader.readStepsPermissions

    fun refreshHealthConnectAccess() {
        viewModelScope.launch {
            val available = healthConnectStepReader.isAvailable()
            val permitted = available && healthConnectStepReader.hasReadStepsPermission()
            healthConnectNeedsPermission.value = available && !permitted
            patchStepDebug {
                it.copy(
                    healthConnectAvailable = available,
                    healthConnectPermission = permitted,
                )
            }
            if (permitted) {
                syncHealthConnectStepsNow()
            }
        }
    }

    fun onHealthConnectPermissionResult() {
        refreshHealthConnectAccess()
    }

    private suspend fun syncHealthConnectStepsNow() {
        val progress = settingsRepository.currentGoalStore().progress ?: return
        val startMs = progress.startedAtEpochMs ?: return
        val steps = healthConnectStepReader.readStepsBetween(
            Instant.ofEpochMilli(startMs),
            Instant.now(),
        ) ?: return
        val count = steps.toInt().coerceAtLeast(0)
        morningGateRepository.applyStepsAccumulated(count)
        patchStepDebug {
            it.copy(
                healthConnectSteps = count,
                lastEvent = stepDebugTimestamp("HC $count steps since start"),
            )
        }
    }

    private fun startStepGoalTracking() {
        stepDetectionJob?.cancel()
        stepDetectionJob = viewModelScope.launch {
            val settings = settingsRepository.currentSettings()
            if (settings.morningGoal.type != GoalType.STEP_COUNT) return@launch

            patchStepDebug {
                it.copy(
                    trackingActive = true,
                    lastEvent = stepDebugTimestamp("tracking start"),
                )
            }

            val healthConnectJob = launch {
                while (isActive && goalScreenForeground.value) {
                    if (healthConnectStepReader.isAvailable() &&
                        healthConnectStepReader.hasReadStepsPermission()
                    ) {
                        syncHealthConnectStepsNow()
                    }
                    delay(2_500)
                }
            }

            val detectorJob = launch {
                if (!stepCounterProvider.hasStepDetector()) return@launch
                stepCounterProvider.stepDetections().collect {
                    morningGateRepository.addDetectedSteps(1)
                    patchStepDebug {
                        it.copy(
                            detectorPulses = it.detectorPulses + 1,
                            lastEvent = stepDebugTimestamp("detector pulse"),
                        )
                    }
                }
            }

            var lastCounter: Int? = null
            while (isActive && goalScreenForeground.value) {
                delay(750)
                val total = currentSteps.value
                if (total != null) {
                    val previous = lastCounter
                    if (previous != null && total > previous) {
                        val delta = total - previous
                        morningGateRepository.addDetectedSteps(delta)
                        patchStepDebug {
                            it.copy(
                                pollStepIncreases = it.pollStepIncreases + delta,
                                lastEvent = stepDebugTimestamp("poll +$delta (→$total)"),
                            )
                        }
                    } else if (previous == null) {
                        morningGateRepository.syncCounterSteps(total)
                        patchStepDebug {
                            it.copy(lastEvent = stepDebugTimestamp("poll baseline $total"))
                        }
                    }
                    lastCounter = total
                }
            }
            healthConnectJob.cancel()
            detectorJob.cancel()
            patchStepDebug {
                it.copy(
                    trackingActive = false,
                    lastEvent = stepDebugTimestamp("tracking stop"),
                )
            }
        }
    }

    private fun publishStepSensorSnapshot(counterListening: Boolean = stepCounterCollecting) {
        patchStepDebug {
            it.copy(
                activityPermission = stepCounterProvider.hasActivityRecognitionPermission(),
                hasStepCounter = stepCounterProvider.hasStepCounter(),
                hasStepDetector = stepCounterProvider.hasStepDetector(),
                counterListening = counterListening,
            )
        }
    }

    private fun patchStepDebug(transform: (StepGoalDebugInfo) -> StepGoalDebugInfo) {
        if (!AppDebugFlags.showDebugPanels) return
        stepDebug.value = transform(stepDebug.value)
    }

    private suspend fun runOnScreenTicker() {
        while (goalScreenForeground.value) {
            kotlinx.coroutines.delay(1_000)
            if (goalScreenForeground.value) {
                tickBibleReadingSecondIfNeeded()
                tickOnScreenSecond()
            }
        }
    }

    private suspend fun tickBibleReadingSecondIfNeeded() {
        val settings = settingsRepository.currentSettings()
        if (!browseOnly.value && settings.morningGoal.type != GoalType.BIBLE_CHAPTER) return
        val store = settingsRepository.currentGoalStore()
        val dateKey = store.progress?.dateKey ?: LocalDate.now().toString()
        val current = store.bibleReadingSecondsByDate[dateKey] ?: 0
        settingsRepository.recordBibleReadingSeconds(dateKey, current + 1)
    }

    private suspend fun tickOnScreenSecond() {
        val settings = settingsRepository.currentSettings()
        if (settings.morningGoal.type == GoalType.STEP_COUNT) return
        val store = settingsRepository.currentGoalStore()
        val progress = store.progress ?: return
        if (progress.isComplete) return
        settingsRepository.updateGoalProgress {
            val current = it.progress ?: return@updateGoalProgress it
            val newSeconds = current.onScreenSeconds + 1
            val dateKey = current.dateKey
            val mergedTimes = mergeGoalScreenTime(it.goalTimeSecondsByDate, dateKey, newSeconds)
            it.copy(
                progress = current.copy(onScreenSeconds = newSeconds),
                goalTimeSecondsByDate = mergedTimes,
            )
        }
    }

    fun setBibleTranslation(translationId: String) {
        viewModelScope.launch {
            settingsRepository.setBibleTranslation(translationId)
            loadBibleIfNeeded()
        }
    }

    fun setBibleBook(book: String) {
        viewModelScope.launch {
            val chapter = settingsRepository.currentSettings().morningGoal.bibleChapter
            settingsRepository.setBibleReading(book, chapter)
            loadBibleIfNeeded()
        }
    }

    fun stepBibleChapter(delta: Int) {
        viewModelScope.launch {
            val goal = settingsRepository.currentSettings().morningGoal
            val (book, chapter) = if (delta > 0) {
                BibleCanon.advance(goal.bibleBook, goal.bibleChapter)
            } else {
                BibleCanon.previous(goal.bibleBook, goal.bibleChapter)
            }
            settingsRepository.setBibleReading(book, chapter)
            loadBibleIfNeeded()
        }
    }

    fun removeBibleHighlight(highlight: BibleHighlight) {
        viewModelScope.launch {
            userDataSyncService.deleteHighlight(highlight)
        }
    }

    fun saveBibleHighlight(request: BibleVerseHighlightRequest) {
        viewModelScope.launch {
            val goal = settingsRepository.currentSettings().morningGoal
            val note = request.note.trim()
            val sorted = request.verses.sortedBy { it.number }
            val refLabel = BibleVerseReferenceFormat.chapterLabel(
                goal.bibleBook,
                goal.bibleChapter,
                sorted.map { it.number },
                BibleTranslations.chipLabel(goal.bibleTranslation),
            )
            bibleHighlightColorStore.set(request.colorKey)
            val numbers = sorted.map { it.number }
            val excerpt = BibleVerseCopyFormat.formatBody(
                sorted.map { BibleVerseLine(it.number, it.text) },
            ).take(4_000)
            if (note.trim().isNotEmpty()) {
                settingsRepository.incrementNoteSaveCountForRecoveryPrompt()
            }
            bibleHighlightRepository.save(
                BibleHighlight(
                    translationId = goal.bibleTranslation,
                    book = goal.bibleBook,
                    chapter = goal.bibleChapter,
                    label = refLabel,
                    excerpt = excerpt,
                    verse = numbers.first(),
                    verseEnd = numbers.last(),
                    verseNumbers = numbers.joinToString(","),
                    colorKey = request.colorKey,
                    note = note,
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
            userDataSyncService.syncHighlightsIfSignedIn().onFailure { /* logged in UserDataSyncService */ }
            _highlightSaved.emit(Unit)
        }
    }

    fun retryBibleChapterLoad() {
        loadBibleIfNeeded(forceRefresh = true)
    }

    fun loadBibleIfNeeded(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val settings = settingsRepository.currentSettings()
            if (!browseOnly.value && settings.morningGoal.type != GoalType.BIBLE_CHAPTER) return@launch
            val goal = settings.morningGoal
            val chapterKey = bibleChapterKey(goal.bibleTranslation, goal.bibleBook, goal.bibleChapter)
            if (!forceRefresh &&
                chapterKey == loadedBibleChapterKey &&
                biblePassage.value.isNotEmpty()
            ) {
                return@launch
            }
            val chapterChanged = chapterKey != loadedBibleChapterKey
            loadingBible.value = true
            bibleLoadError.value = null
            if (chapterChanged) {
                biblePassage.value = emptyList()
            }
            val result = bibleRepository.loadChapter(
                goal.bibleTranslation,
                goal.bibleBook,
                goal.bibleChapter,
                forceRefresh = forceRefresh,
            )
            loadingBible.value = false
            result.onSuccess {
                loadedBibleChapterKey = chapterKey
                biblePassage.value = it.blocks
                bibleLoadError.value = null
                prefetchAdjacentChapters(goal)
            }.onFailure {
                if (chapterChanged) {
                    biblePassage.value = emptyList()
                }
                bibleLoadError.value = it.message ?: "Could not load this chapter."
            }
        }
    }

    private fun bibleChapterKey(translation: String, book: String, chapter: Int): String =
        "${BibleTranslations.normalizeId(translation)}|$book|$chapter"

    private fun prefetchAdjacentChapters(goal: MorningGoalConfig) {
        biblePrefetchJob?.cancel()
        biblePrefetchJob = viewModelScope.launch {
            val (nextBook, nextChapter) = BibleCanon.advance(goal.bibleBook, goal.bibleChapter)
            bibleRepository.prefetchChapter(goal.bibleTranslation, nextBook, nextChapter)
            val (prevBook, prevChapter) = BibleCanon.previous(goal.bibleBook, goal.bibleChapter)
            bibleRepository.prefetchChapter(goal.bibleTranslation, prevBook, prevChapter)
        }
    }

    fun completeGoal() {
        viewModelScope.launch {
            val result = morningGateRepository.completeGoal(currentSteps.value)
            result.onSuccess {
                val held = notificationCaptureRepository.snapshotDigestSummary().count
                _goalCompleted.emit(held)
                applicationScope.launch {
                    analyticsTracker.track("goal_completed")
                    userDataSyncService.syncMorningHistoryIfSignedIn()
                        .onFailure { /* logged in UserDataSyncService */ }
                }
                loadBibleIfNeeded()
            }.onFailure {
                error.value = it.message
            }
        }
    }
}

private fun mergeGoalScreenTime(
    map: Map<String, Int>,
    dateKey: String,
    seconds: Int,
): Map<String, Int> {
    val merged = maxOf(map[dateKey] ?: 0, seconds)
    return if (merged == map[dateKey]) map else map + (dateKey to merged)
}
