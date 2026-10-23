package com.morningguard.data.gate

import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.gate.PeaceBreakPolicy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeaceSessionRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val morningGateRepository: MorningGateRepository,
) {
    suspend fun peaceSessionActiveUntil(nowMs: Long = System.currentTimeMillis()): Long? {
        reconcileStoredPeaceSession(nowMs)
        val until = settingsRepository.currentGoalStore().peaceSessionUntilEpochMs ?: return null
        return if (nowMs < until) until else null
    }

    suspend fun startOrExtendOneHour(nowMs: Long = System.currentTimeMillis()): Long {
        reconcileStoredPeaceSession(nowMs)
        val store = settingsRepository.currentGoalStore()
        val peaceActive = store.peaceSessionUntilEpochMs?.let { it > nowMs } == true
        val gate = morningGateRepository.evaluateNow()
        if (!peaceActive && PeaceBreakPolicy.isMorningGoalActive(gate)) {
            return store.peaceSessionUntilEpochMs ?: nowMs
        }
        val base = store.peaceSessionUntilEpochMs?.takeIf { it > nowMs } ?: nowMs
        val until = base + SESSION_DURATION_MS
        settingsRepository.updateGoalProgress { it.copy(peaceSessionUntilEpochMs = until) }
        return until
    }

    suspend fun endPeaceSession() {
        settingsRepository.updateGoalProgress { it.copy(peaceSessionUntilEpochMs = null) }
    }

    /** Drops expired peace timers only. */
    suspend fun reconcileStoredPeaceSession(nowMs: Long = System.currentTimeMillis()) {
        val store = settingsRepository.currentGoalStore()
        val until = store.peaceSessionUntilEpochMs ?: return
        if (nowMs >= until) {
            settingsRepository.updateGoalProgress { it.copy(peaceSessionUntilEpochMs = null) }
        }
    }

    companion object {
        const val SESSION_DURATION_MS = 60 * 60 * 1000L
    }
}
