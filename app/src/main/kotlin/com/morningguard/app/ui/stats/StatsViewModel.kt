package com.morningguard.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.history.MorningDayOutcome
import com.morningguard.domain.history.MorningDayRecord
import com.morningguard.domain.history.MorningStreakCalculator
import com.morningguard.domain.model.GoalType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class StatsUiState(
    val morningGoalType: GoalType = GoalType.BIBLE_CHAPTER,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val completedDays: Int = 0,
    val bypassDays: Int = 0,
    val totalGoalTimeSeconds: Int = 0,
    val totalBibleReadingSeconds: Int = 0,
    val todaySteps: Int = 0,
    val totalGoalSteps: Int = 0,
    val recent: List<MorningDayRecord> = emptyList(),
) {
    val showTimeOnGoalStat: Boolean get() = morningGoalType != GoalType.STEP_COUNT
    val showStepsStat: Boolean get() = morningGoalType == GoalType.STEP_COUNT
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val zoneId: ZoneId,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        settingsRepository.settings,
        settingsRepository.goalProgressStore,
    ) { settings, store ->
        val todayKey = LocalDate.now(zoneId).toString()
        val history = store.morningHistory
        val progress = store.progress
        val goalType = settings.morningGoal.type
        val activeProgress = progress?.takeIf { !it.isComplete && it.dateKey == todayKey }
        val activeSeconds = activeProgress?.onScreenSeconds.takeIf { goalType != GoalType.STEP_COUNT }
        val activeSteps = activeProgress?.stepsAccumulated.takeIf { goalType == GoalType.STEP_COUNT }
        StatsUiState(
            morningGoalType = goalType,
            currentStreak = MorningStreakCalculator.currentCompletedStreak(history, todayKey),
            bestStreak = MorningStreakCalculator.bestCompletedStreak(history),
            completedDays = MorningStreakCalculator.countOutcome(history, MorningDayOutcome.GOAL_COMPLETED),
            bypassDays = MorningStreakCalculator.countOutcome(history, MorningDayOutcome.EMERGENCY_BYPASS),
            totalGoalTimeSeconds = if (goalType == GoalType.STEP_COUNT) {
                0
            } else {
                MorningStreakCalculator.totalGoalTimeSeconds(
                    dailySeconds = store.goalTimeSecondsByDate,
                    history = history,
                    activeProgressSeconds = activeSeconds,
                    activeDateKey = todayKey.takeIf { activeSeconds != null },
                )
            },
            todaySteps = if (goalType != GoalType.STEP_COUNT) {
                0
            } else {
                activeSteps
                    ?: store.goalStepsByDate[todayKey]
                    ?: history.find { it.dateKey == todayKey }?.stepsOnGoal
                    ?: 0
            },
            totalGoalSteps = if (goalType == GoalType.STEP_COUNT) {
                MorningStreakCalculator.totalGoalSteps(
                    dailySteps = store.goalStepsByDate,
                    history = history,
                    activeProgressSteps = activeSteps,
                    activeDateKey = todayKey.takeIf { activeSteps != null },
                )
            } else {
                0
            },
            totalBibleReadingSeconds = MorningStreakCalculator.totalBibleReadingSeconds(
                store.bibleReadingSecondsByDate,
            ),
            recent = history.sortedByDescending { it.dateKey },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
