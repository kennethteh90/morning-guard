package com.morningguard.data.gate

import com.morningguard.data.settings.GoalProgressStore
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.gate.GateEvaluation
import com.morningguard.domain.gate.GoalCompletionVerifier
import com.morningguard.domain.gate.MorningGateEvaluator
import com.morningguard.domain.history.MorningDayOutcome
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningGoalProgress
import com.morningguard.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val goalProgressMutex = Mutex()
    val gateEvaluation: Flow<GateEvaluation> = combine(
        settingsRepository.settings,
        settingsRepository.goalProgressStore,
    ) { settings, store ->
        evaluate(settings, store)
    }

    suspend fun evaluateNow(): GateEvaluation {
        val settings = settingsRepository.currentSettings()
        val store = settingsRepository.currentGoalStore()
        return evaluate(settings, store)
    }

    suspend fun currentSettingsAndEvaluation(): Pair<UserSettings, GateEvaluation> {
        val settings = settingsRepository.currentSettings()
        val store = settingsRepository.currentGoalStore()
        return settings to evaluate(settings, store)
    }

    private fun evaluate(
        settings: UserSettings,
        store: GoalProgressStore,
    ): GateEvaluation {
        return evaluator.evaluate(
            window = settings.morningWindow,
            progress = store.progress,
            emergencyBypassActiveUntilEpochMs = store.emergencyBypassUntilEpochMs,
            peaceSessionUntilEpochMs = store.peaceSessionUntilEpochMs,
        )
    }

    suspend fun startGoalIfNeeded(): MorningGoalProgress = goalProgressMutex.withLock {
        val settings = settingsRepository.currentSettings()
        val dateKey = LocalDate.now(zoneId).toString()
        val store = settingsRepository.currentGoalStore()
        val existing = store.progress
        if (existing?.dateKey == dateKey) {
            return@withLock syncProgressWithSettings(existing, settings.morningGoal)
        }

        val fresh = MorningGoalProgress(
            dateKey = dateKey,
            config = settings.morningGoal,
            startedAtEpochMs = System.currentTimeMillis(),
        )
        settingsRepository.updateGoalProgress { it.copy(progress = fresh) }
        fresh
    }

    suspend fun applyStepsAccumulated(steps: Int) = goalProgressMutex.withLock {
        if (steps <= 0) return@withLock
        val settings = settingsRepository.currentSettings()
        if (settings.morningGoal.type != GoalType.STEP_COUNT) return@withLock
        val progress = startGoalIfNeededUnlocked(settings)
        if (progress.isComplete) return@withLock
        if (steps <= progress.stepsAccumulated) return@withLock
        val updated = progress.copy(stepsAccumulated = steps)
        settingsRepository.updateGoalProgress { it.copy(progress = updated) }
        settingsRepository.recordGoalStepsForDate(updated.dateKey, updated.stepsAccumulated)
    }

    suspend fun addDetectedSteps(count: Int = 1) = goalProgressMutex.withLock {
        if (count <= 0) return@withLock
        val settings = settingsRepository.currentSettings()
        if (settings.morningGoal.type != GoalType.STEP_COUNT) return@withLock
        val progress = startGoalIfNeededUnlocked(settings)
        if (progress.isComplete) return@withLock
        val updated = progress.copy(stepsAccumulated = progress.stepsAccumulated + count)
        settingsRepository.updateGoalProgress { it.copy(progress = updated) }
        settingsRepository.recordGoalStepsForDate(updated.dateKey, updated.stepsAccumulated)
    }

    suspend fun syncCounterSteps(currentTotal: Int) = goalProgressMutex.withLock {
        val settings = settingsRepository.currentSettings()
        if (settings.morningGoal.type != GoalType.STEP_COUNT) return@withLock
        val progress = startGoalIfNeededUnlocked(settings)
        if (progress.isComplete) return@withLock
        val withBaseline = if (progress.stepsAtStart == null) {
            progress.copy(stepsAtStart = currentTotal)
        } else {
            progress
        }
        val delta = (currentTotal - (withBaseline.stepsAtStart ?: currentTotal)).coerceAtLeast(0)
        val merged = withBaseline.copy(
            stepsAccumulated = maxOf(withBaseline.stepsAccumulated, delta),
        )
        if (merged != progress) {
            settingsRepository.updateGoalProgress { it.copy(progress = merged) }
            settingsRepository.recordGoalStepsForDate(merged.dateKey, merged.stepsAccumulated)
        }
    }

    private suspend fun startGoalIfNeededUnlocked(settings: UserSettings): MorningGoalProgress {
        val dateKey = LocalDate.now(zoneId).toString()
        val store = settingsRepository.currentGoalStore()
        val existing = store.progress
        if (existing?.dateKey == dateKey) {
            return syncProgressWithSettings(existing, settings.morningGoal)
        }
        val fresh = MorningGoalProgress(
            dateKey = dateKey,
            config = settings.morningGoal,
            startedAtEpochMs = System.currentTimeMillis(),
        )
        settingsRepository.updateGoalProgress { it.copy(progress = fresh) }
        return fresh
    }

    suspend fun completeGoal(currentSteps: Int? = null): Result<Unit> {
        val settings = settingsRepository.currentSettings()
        val store = settingsRepository.currentGoalStore()
        val progress = store.progress ?: return Result.failure(IllegalStateException("No active goal"))
        val activeConfig = settings.morningGoal
        if (!completionVerifier.canMarkComplete(progress, activeConfig, currentSteps)) {
            return Result.failure(IllegalStateException("Goal requirements not met yet"))
        }
        if (activeConfig.type == GoalType.STEP_COUNT) {
            settingsRepository.updateGoalProgress {
                it.copy(progress = progress.copy(completedAtEpochMs = System.currentTimeMillis()))
            }
            settingsRepository.recordMorningOutcome(
                dateKey = progress.dateKey,
                outcome = MorningDayOutcome.GOAL_COMPLETED,
                trackGoalTime = false,
                stepsOnGoal = progress.stepsAccumulated,
            )
        } else {
            var completedConfig = activeConfig
            if (activeConfig.type == GoalType.BIBLE_CHAPTER) {
                settingsRepository.advanceBibleReadingAfterComplete()
                completedConfig = settingsRepository.currentSettings().morningGoal
            }
            settingsRepository.updateGoalProgress {
                it.copy(
                    progress = progress.copy(
                        completedAtEpochMs = System.currentTimeMillis(),
                        config = completedConfig,
                    ),
                )
            }
            settingsRepository.recordMorningOutcome(progress.dateKey, MorningDayOutcome.GOAL_COMPLETED)
        }
        return Result.success(Unit)
    }

    fun canComplete(
        activeConfig: MorningGoalConfig,
        progress: MorningGoalProgress?,
        currentSteps: Int?,
    ): Boolean {
        progress ?: return false
        return completionVerifier.canMarkComplete(progress, activeConfig, currentSteps)
    }

    private suspend fun syncProgressWithSettings(
        existing: MorningGoalProgress,
        morningGoal: MorningGoalConfig,
    ): MorningGoalProgress {
        if (existing.isComplete) return existing
        if (existing.config == morningGoal) return existing

        val updated = if (existing.config.type != morningGoal.type) {
            existing.copy(
                config = morningGoal,
                onScreenSeconds = 0,
                stepsAtStart = null,
                stepsAccumulated = 0,
                startedAtEpochMs = System.currentTimeMillis(),
            )
        } else {
            existing.copy(config = morningGoal)
        }
        settingsRepository.updateGoalProgress { it.copy(progress = updated) }
        return updated
    }
}
