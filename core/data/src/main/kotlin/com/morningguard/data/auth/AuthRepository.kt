package com.morningguard.data.auth

import com.morningguard.data.security.NoteEncryptionRepository
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.model.DataStorageMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AuthUser(
    val id: String,
    val email: String?,
)

sealed class AuthState {
    data object Unconfigured : AuthState()
    data object SignedOut : AuthState()
    data class SignedIn(val user: AuthUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

interface AuthBackend {
    val state: StateFlow<AuthState>
    suspend fun signInWithMagicLink(email: String): Result<Unit>
    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun deleteCurrentUser(): Result<Unit>
    suspend fun restoreSession(): Result<Unit>
}

@Singleton
class AuthRepository @Inject constructor(
    private val backend: AuthBackend,
    private val userLocalDataCleaner: UserLocalDataCleaner,
    private val magicLinkRateLimitStore: MagicLinkRateLimitStore,
    private val settingsRepository: SettingsRepository,
    private val noteEncryptionRepository: NoteEncryptionRepository,
) {
    val state: StateFlow<AuthState> = backend.state

    suspend fun sendMagicLink(email: String): Result<Unit> {
        val normalized = email.trim()
        val waitSec = magicLinkRateLimitStore.secondsUntilResend(normalized)
        if (waitSec > 0) {
            return Result.failure(MagicLinkRateLimitedException(waitSec))
        }
        return backend.signInWithMagicLink(normalized).onSuccess {
            magicLinkRateLimitStore.recordSent(normalized)
            settingsRepository.updateSettings {
                it.copy(dataStorageMode = DataStorageMode.CLOUD_SYNC)
            }
        }
    }

    suspend fun signInWithGoogle(idToken: String) =
        backend.signInWithGoogleIdToken(idToken).onSuccess {
            settingsRepository.updateSettings {
                it.copy(dataStorageMode = DataStorageMode.CLOUD_SYNC)
            }
        }

    suspend fun signOut(): Result<Unit> {
        userLocalDataCleaner.clearOnSignOut()
        noteEncryptionRepository.lock()
        settingsRepository.updateSettings {
            it.copy(dataStorageMode = DataStorageMode.LOCAL_ONLY)
        }
        return backend.signOut()
    }

    /** Ends the remote session without wiping local Room/DataStore data (e.g. after backup restore). */
    suspend fun revokeRemoteSessionOnly(): Result<Unit> = backend.signOut()

    suspend fun deleteAccount(): Result<Unit> {
        val result = backend.deleteCurrentUser()
        if (result.isSuccess) {
            userLocalDataCleaner.clearOnSignOut()
        }
        return result
    }

    suspend fun restoreSession() = backend.restoreSession()
}

class UnconfiguredAuthBackend @Inject constructor() : AuthBackend {
    private val _state = MutableStateFlow<AuthState>(AuthState.Unconfigured)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    override suspend fun signInWithMagicLink(email: String): Result<Unit> =
        Result.failure(IllegalStateException("Supabase is not configured"))

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> =
        Result.failure(IllegalStateException("Supabase is not configured"))

    override suspend fun signOut(): Result<Unit> {
        _state.value = AuthState.Unconfigured
        return Result.success(Unit)
    }

    override suspend fun deleteCurrentUser(): Result<Unit> =
        Result.failure(IllegalStateException("Supabase is not configured"))

    override suspend fun restoreSession(): Result<Unit> = Result.success(Unit)
}
