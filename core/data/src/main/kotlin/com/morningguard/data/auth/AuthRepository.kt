package com.morningguard.data.auth

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
    suspend fun restoreSession(): Result<Unit>
}

@Singleton
class AuthRepository @Inject constructor(
    private val backend: AuthBackend,
) {
    val state: StateFlow<AuthState> = backend.state

    suspend fun sendMagicLink(email: String) = backend.signInWithMagicLink(email)
    suspend fun signInWithGoogle(idToken: String) = backend.signInWithGoogleIdToken(idToken)
    suspend fun signOut() = backend.signOut()
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

    override suspend fun restoreSession(): Result<Unit> = Result.success(Unit)
}
