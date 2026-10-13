package com.morningguard.data.gate

import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.gate.GateEvaluation
import com.morningguard.domain.gate.GoalCompletionVerifier
import com.morningguard.domain.gate.MorningGateEvaluator
import com.morningguard.domain.model.MorningGoalProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MorningGateRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val evaluator: MorningGateEvaluator,
    private val completionVerifier: GoalCompletionVerifier,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val gateEvaluation: Flow<GateEvaluation> = combine(
        settingsRepository.settings,
        settingsRepository.goalProgressStore,
    ) { settings, store ->
        evaluator.evaluate(
            window = settings.morningWindow,
            progress = store.progress,
            emergencyBypassActiveUntilEpochMs = store.emergencyBypassUntilEpochMs,
        )
    }

    suspend fun startGoalIfNeeded(stepsAtStart: Int? = null): MorningGoalProgress {
        val settings = settingsRepository.currentSettings()
        val dateKey = LocalDate.now(zoneId).toString()
        val store = settingsRepository.currentGoalStore()
        val existing = store.progress
        if (existing?.dateKey == dateKey) return existing

        val fresh = MorningGoalProgress(
            dateKey = dateKey,
            config = settings.morningGoal,
            startedAtEpochMs = System.currentTimeMillis(),
            stepsAtStart = stepsAtStart,
        )
        settingsRepository.updateGoalProgress { it.copy(progress = fresh) }
        return fresh
    }

    suspend fun completeGoal(currentSteps: Int? = null): Result<Unit> {
        val store = settingsRepository.currentGoalStore()
        val progress = store.progress ?: return Result.failure(IllegalStateException("No active goal"))
        if (!completionVerifier.canMarkComplete(progress, currentSteps)) {
            return Result.failure(IllegalStateException("Goal requirements not met yet"))
        }
        settingsRepository.updateGoalProgress {
            it.copy(progress = progress.copy(completedAtEpochMs = System.currentTimeMillis()))
        }
        return Result.success(Unit)
    }

    fun canComplete(progress: MorningGoalProgress?, currentSteps: Int?): Boolean {
        progress ?: return false
        return completionVerifier.canMarkComplete(progress, currentSteps)
    }
}
