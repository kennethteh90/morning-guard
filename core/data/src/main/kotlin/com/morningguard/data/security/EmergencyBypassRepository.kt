package com.morningguard.data.security

import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.security.EmergencyBypassPolicy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyBypassRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val hasher: PassphraseHasher,
    private val policy: EmergencyBypassPolicy,
) {
    suspend fun setPassphrase(passphrase: String): Result<Unit> {
        if (!policy.validateLength(passphrase)) {
            return Result.failure(IllegalArgumentException("Passphrase must be at least 8 characters"))
        }
        val salt = hasher.generateSalt()
        val hash = hasher.hash(passphrase, salt)
        settingsRepository.updateGoalProgress {
            it.copy(emergencyPassphraseHash = hash, emergencyPassphraseSalt = salt)
        }
        return Result.success(Unit)
    }

    suspend fun attemptBypass(passphrase: String): Result<Long> {
        val store = settingsRepository.currentGoalStore()
        val hash = store.emergencyPassphraseHash
        val salt = store.emergencyPassphraseSalt
        if (hash == null || salt == null) {
            return Result.failure(IllegalStateException("Emergency passphrase is not configured"))
        }
        val candidate = hasher.hash(passphrase, salt)
        if (!policy.constantTimeEquals(candidate, hash)) {
            return Result.failure(SecurityException("Invalid passphrase"))
        }
        val expires = policy.bypassExpiresAt()
        settingsRepository.updateGoalProgress {
            it.copy(emergencyBypassUntilEpochMs = expires)
        }
        return Result.success(expires)
    }
}
