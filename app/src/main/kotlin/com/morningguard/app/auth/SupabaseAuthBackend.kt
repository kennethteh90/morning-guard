package com.morningguard.app.auth

import android.content.Intent
import com.morningguard.app.BuildConfig
import com.morningguard.app.supabase.SupabaseClientProvider
import com.morningguard.data.auth.AuthBackend
import com.morningguard.data.auth.AuthState
import com.morningguard.data.auth.AuthUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

class SupabaseAuthBackend(
    clientProvider: SupabaseClientProvider,
    private val okHttp: OkHttpClient,
) : AuthBackend {

    companion object {
        /** Must match AndroidManifest intent-filter and Supabase Auth → Redirect URLs. */
        const val MAGIC_LINK_REDIRECT = "morningguard://auth"
    }

    private val client: SupabaseClient = requireNotNull(clientProvider.client) {
        "Supabase client is not configured"
    }

    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    override suspend fun signInWithMagicLink(email: String): Result<Unit> {
        val normalized = email.trim()
        return try {
            client.auth.signInWith(OTP, MAGIC_LINK_REDIRECT) {
                this.email = normalized
            }
            _state.value = AuthState.SignedOut
            Result.success(Unit)
        } catch (e: Exception) {
            val message = magicLinkErrorMessage(e)
            _state.value = AuthState.Error(message)
            Result.failure(IllegalStateException(message))
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
        } catch (e: Exception) {
            _state.value = AuthState.Error(e.message ?: "Google sign-in failed")
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            client.auth.signOut()
            _state.value = AuthState.SignedOut
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteCurrentUser(): Result<Unit> {
        val session = client.auth.currentSessionOrNull()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/user"
        return try {
            val response = okHttp.newCall(
                Request.Builder()
                    .url(url)
                    .delete()
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .build(),
            ).execute()
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException("Account deletion failed (${response.code})"),
                )
            }
            client.auth.signOut()
            _state.value = AuthState.SignedOut
            Result.success(Unit)
        } catch (e: Exception) {
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
        } catch (_: Exception) {
            _state.value = AuthState.SignedOut
            Result.success(Unit)
        }
    }

    suspend fun handleDeepLink(intent: Intent): Result<Unit> {
        return try {
            val uri = intent.data ?: return Result.failure(IllegalArgumentException("Missing sign-in callback"))
            val pkceCode = uri.getQueryParameter("code")
                ?: return Result.failure(IllegalArgumentException("Missing PKCE code"))
            client.auth.exchangeCodeForSession(pkceCode)
            syncUser()
            if (_state.value !is AuthState.SignedIn) {
                val message = "Could not finish sign-in. Request a new magic link and open it on this phone."
                _state.value = AuthState.Error(message)
                return Result.failure(IllegalStateException(message))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val message = e.message ?: "Sign-in failed"
            _state.value = AuthState.Error(message)
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

    private fun magicLinkErrorMessage(e: Exception): String {
        if (e is RestException && e.statusCode == 429) {
            return "Too many sign-in emails from Supabase. Wait a few minutes, then try again."
        }
        val raw = e.message.orEmpty()
        if (raw.contains("over_email_send_rate_limit", ignoreCase = true) ||
            raw.contains("rate limit", ignoreCase = true)
        ) {
            return "Too many sign-in emails. Wait a few minutes, then try again."
        }
        return raw.ifBlank { "Magic link failed" }
    }
}
