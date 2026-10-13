package com.morningguard.app.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.sensors.StepCounterProvider
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.bible.BibleRepository
import com.morningguard.data.gate.MorningGateRepository
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningGoalProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MorningGoalUiState(
    val config: MorningGoalConfig = MorningGoalConfig(type = GoalType.BIBLE_CHAPTER),
    val progress: MorningGoalProgress? = null,
    val bibleText: String = "",
    val loadingBible: Boolean = false,
    val error: String? = null,
    val currentSteps: Int? = null,
    val canComplete: Boolean = false,
)

@HiltViewModel
class MorningGoalViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val morningGateRepository: MorningGateRepository,
    private val bibleRepository: BibleRepository,
    private val stepCounterProvider: StepCounterProvider,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {

    private val bibleText = MutableStateFlow("")
    private val loadingBible = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val currentSteps = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<MorningGoalUiState> = combine(
        combine(settingsRepository.settings, settingsRepository.goalProgressStore) { settings, store ->
            settings to store
        },
        combine(bibleText, loadingBible, error, currentSteps) { text, loading, err, steps ->
            Quad(text, loading, err, steps)
        },
    ) { (settings, store), quad ->
        val progress = store.progress
        MorningGoalUiState(
            config = settings.morningGoal,
            progress = progress,
            bibleText = quad.first,
            loadingBible = quad.second,
            error = quad.third,
            currentSteps = quad.fourth,
            canComplete = morningGateRepository.canComplete(progress, quad.fourth),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MorningGoalUiState())

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    init {
        if (stepCounterProvider.isAvailable) {
            viewModelScope.launch {
                stepCounterProvider.stepCounter().collect { steps ->
                    currentSteps.value = steps
                    if (settingsRepository.currentSettings().morningGoal.type == GoalType.STEP_COUNT) {
                        morningGateRepository.startGoalIfNeeded(stepsAtStart = steps)
                    }
                }
            }
        }
        viewModelScope.launch {
            morningGateRepository.startGoalIfNeeded()
            loadBibleIfNeeded()
        }
    }

    fun loadBibleIfNeeded() {
        viewModelScope.launch {
            val settings = settingsRepository.currentSettings()
            if (settings.morningGoal.type != GoalType.BIBLE_CHAPTER) return@launch
            loadingBible.value = true
            error.value = null
            val result = bibleRepository.loadChapter(
                settings.morningGoal.bibleTranslation,
                settings.morningGoal.bibleBook,
                settings.morningGoal.bibleChapter,
            )
            loadingBible.value = false
            result.onSuccess { bibleText.value = it }
                .onFailure { error.value = it.message }
        }
    }

    fun completeGoal() {
        viewModelScope.launch {
            val result = morningGateRepository.completeGoal(currentSteps.value)
            result.onSuccess {
                analyticsTracker.track("goal_completed")
            }.onFailure {
                error.value = it.message
            }
        }
    }
}
