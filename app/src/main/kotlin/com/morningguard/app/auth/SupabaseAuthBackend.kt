package com.morningguard.app.auth

import android.content.Context
import android.content.Intent
import com.morningguard.data.auth.AuthBackend
import com.morningguard.data.auth.AuthState
import com.morningguard.data.auth.AuthUser
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SupabaseAuthBackend(
    @Suppress("UnusedPrivateProperty") context: Context,
    supabaseUrl: String,
    supabaseAnonKey: String,
) : AuthBackend {

    private val client = createSupabaseClient(supabaseUrl, supabaseAnonKey) {
        install(Auth)
    }

    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    override suspend fun signInWithMagicLink(email: String): Result<Unit> {
        return try {
            client.auth.signInWith(OTP) {
                this.email = email
            }
            _state.value = AuthState.SignedOut
            Result.success(Unit)
        } catch (e: RuntimeException) {
            _state.value = AuthState.Error(e.message ?: "Magic link failed")
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> {
        return try {
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }
            syncUser()
            Result.success(Unit)
        } catch (e: RuntimeException) {
            _state.value = AuthState.Error(e.message ?: "Google sign-in failed")
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            client.auth.signOut()
            _state.value = AuthState.SignedOut
            Result.success(Unit)
        } catch (e: RuntimeException) {
            Result.failure(e)
        }
    }

    override suspend fun restoreSession(): Result<Unit> {
        return try {
            val session = client.auth.currentSessionOrNull()
            if (session == null) {
                _state.value = AuthState.SignedOut
            } else {
                syncUser()
            }
            Result.success(Unit)
        } catch (_: RuntimeException) {
            _state.value = AuthState.SignedOut
            Result.success(Unit)
        }
    }

    suspend fun handleDeepLink(intent: Intent): Result<Unit> {
        return try {
            client.handleDeeplinks(intent)
            syncUser()
            Result.success(Unit)
        } catch (e: RuntimeException) {
            Result.failure(e)
        }
    }

    private suspend fun syncUser() {
        val user = client.auth.currentUserOrNull()
        _state.value = if (user == null) {
            AuthState.SignedOut
        } else {
            AuthState.SignedIn(AuthUser(id = user.id, email = user.email))
        }
    }
}
