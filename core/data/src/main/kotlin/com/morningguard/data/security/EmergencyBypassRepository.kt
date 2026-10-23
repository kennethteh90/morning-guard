package com.morningguard.data.security

import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.history.MorningDayOutcome
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.security.EmergencyBypassPolicy
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyBypassRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val hasher: PassphraseHasher,
    private val policy: EmergencyBypassPolicy,
    private val zoneId: ZoneId,
) {
    suspend fun hasPassphraseConfigured(): Boolean {
        val hash = settingsRepository.currentGoalStore().emergencyPassphraseHash
        return !hash.isNullOrBlank() && hasher.isModernFormat(hash)
    }

    suspend fun setPassphrase(passphrase: String): Result<Unit> {
        if (!policy.validateLength(passphrase)) {
            return Result.failure(IllegalArgumentException("Passphrase must be at least 8 characters"))
        }
        val stored = hasher.hash(passphrase)
        settingsRepository.updateGoalProgress {
            it.copy(
                emergencyPassphraseHash = stored,
                emergencyPassphraseSalt = null,
                failedAttempts = 0,
                lockedUntilEpochMs = null,
            )
        }
        return Result.success(Unit)
    }

    suspend fun attemptBypass(passphrase: String): Result<Long> {
        val store = settingsRepository.currentGoalStore()
        if (policy.isLocked(store.lockedUntilEpochMs)) {
            return Result.failure(SecurityException(LOCKED_MESSAGE))
        }
        val stored = store.emergencyPassphraseHash
        if (stored == null) {
            return Result.failure(IllegalStateException("Emergency passphrase is not configured"))
        }
        if (!hasher.isModernFormat(stored)) {
            return Result.failure(SecurityException(LEGACY_PASSPHRASE_MESSAGE))
        }
        if (!hasher.verify(passphrase, stored)) {
            val (attempts, lockedUntil) = policy.recordFailure(
                store.failedAttempts,
                store.lockedUntilEpochMs,
            )
            settingsRepository.updateGoalProgress {
                it.copy(failedAttempts = attempts, lockedUntilEpochMs = lockedUntil)
            }
            return Result.failure(SecurityException(INVALID_PASSPHRASE_MESSAGE))
        }
        val expires = policy.bypassExpiresAt()
        val (clearedAttempts, clearedLock) = policy.clearedLockoutAfterSuccess()
        settingsRepository.updateGoalProgress {
            it.copy(
                emergencyBypassUntilEpochMs = expires,
                failedAttempts = clearedAttempts,
                lockedUntilEpochMs = clearedLock,
            )
        }
        val dateKey = LocalDate.now(zoneId).toString()
        val trackGoalTime = settingsRepository.currentSettings().morningGoal.type != GoalType.STEP_COUNT
        settingsRepository.recordMorningOutcome(
            dateKey,
            MorningDayOutcome.EMERGENCY_BYPASS,
            trackGoalTime = trackGoalTime,
        )
        return Result.success(expires)
    }

    companion object {
        const val LEGACY_PASSPHRASE_MESSAGE = "Set a new emergency passphrase"
        private const val INVALID_PASSPHRASE_MESSAGE = "Invalid passphrase"
        private const val LOCKED_MESSAGE = "Too many failed attempts. Try again in 15 minutes."
    }
}
