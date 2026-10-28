package com.morningguard.app.ui.auth

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.BuildConfig
import com.morningguard.app.R
import com.morningguard.app.auth.GoogleSignInHelper
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import com.morningguard.data.auth.MagicLinkRateLimitedException
import com.morningguard.data.auth.MagicLinkRateLimitStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MagicLinkUiPhase {
    EnterEmail,
    AwaitingInbox,
}

data class AuthUiState(
    val magicLinkPhase: MagicLinkUiPhase = MagicLinkUiPhase.EnterEmail,
    val pendingEmail: String? = null,
    val isSendingMagicLink: Boolean = false,
    val isSigningInWithGoogle: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val statusMessage: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
    private val magicLinkRateLimitStore: MagicLinkRateLimitStore,
    private val googleSignInHelper: GoogleSignInHelper,
) : ViewModel() {

    private val _local = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _local.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Unconfigured)

    fun isGoogleSignInEnabled(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    init {
        viewModelScope.launch {
            authRepository.state.collect { state ->
                if (state is AuthState.SignedIn) {
                    _local.value = AuthUiState()
                }
            }
        }
    }

    fun refreshResendCooldown() {
        val email = _local.value.pendingEmail ?: return
        val seconds = magicLinkRateLimitStore.secondsUntilResend(email)
        if (_local.value.resendCooldownSeconds != seconds) {
            _local.value = _local.value.copy(resendCooldownSeconds = seconds)
        }
    }

    fun sendMagicLink(email: String) {
        val normalized = email.trim()
        if (normalized.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            _local.value = _local.value.copy(
                magicLinkPhase = MagicLinkUiPhase.EnterEmail,
                error = "Enter a valid email address.",
            )
            return
        }
        val clientWait = magicLinkRateLimitStore.secondsUntilResend(normalized)
        if (clientWait > 0) {
            _local.value = _local.value.copy(
                magicLinkPhase = MagicLinkUiPhase.AwaitingInbox,
                pendingEmail = normalized,
                resendCooldownSeconds = clientWait,
                error = appContext.getString(R.string.auth_magic_link_rate_limit, clientWait),
            )
            return
        }
        requestMagicLink(normalized)
    }

    fun resendMagicLink() {
        val email = _local.value.pendingEmail ?: return
        sendMagicLink(email)
    }

    fun editMagicLinkEmail() {
        _local.value = _local.value.copy(
            magicLinkPhase = MagicLinkUiPhase.EnterEmail,
            pendingEmail = null,
            error = null,
            statusMessage = null,
        )
    }

    private fun requestMagicLink(normalized: String) {
        viewModelScope.launch {
            _local.value = _local.value.copy(
                isSendingMagicLink = true,
                error = null,
                pendingEmail = normalized,
            )
            authRepository.sendMagicLink(normalized)
                .onSuccess {
                    _local.value = AuthUiState(
                        magicLinkPhase = MagicLinkUiPhase.AwaitingInbox,
                        pendingEmail = normalized,
                        resendCooldownSeconds = magicLinkRateLimitStore.secondsUntilResend(normalized),
                        statusMessage = appContext.getString(R.string.auth_magic_link_inbox_title),
                    )
                }
                .onFailure { error ->
                    when (error) {
                        is MagicLinkRateLimitedException -> {
                            _local.value = AuthUiState(
                                magicLinkPhase = MagicLinkUiPhase.AwaitingInbox,
                                pendingEmail = normalized,
                                resendCooldownSeconds = error.retryAfterSeconds,
                                error = appContext.getString(
                                    R.string.auth_magic_link_rate_limit,
                                    error.retryAfterSeconds,
                                ),
                            )
                        }
                        else -> {
                            _local.value = _local.value.copy(
                                magicLinkPhase = MagicLinkUiPhase.EnterEmail,
                                isSendingMagicLink = false,
                                error = error.message ?: "Could not send magic link.",
                            )
                        }
                    }
                }
            if (_local.value.isSendingMagicLink) {
                _local.value = _local.value.copy(isSendingMagicLink = false)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            authRepository.deleteAccount()
                .onSuccess {
                    _local.value = AuthUiState(
                        statusMessage = "Your account was deleted.",
                    )
                }
                .onFailure {
                    _local.value = _local.value.copy(error = it.message)
                }
        }
    }

    fun signInWithGoogle(context: Context) {
        if (!isGoogleSignInEnabled()) {
            _local.value = _local.value.copy(error = "Google sign-in is not configured for this build.")
            return
        }
        viewModelScope.launch {
            _local.value = _local.value.copy(isSigningInWithGoogle = true, error = null)
            googleSignInHelper.requestIdToken(context)
                .onSuccess { token ->
                    authRepository.signInWithGoogle(token)
                        .onFailure { error ->
                            _local.value = _local.value.copy(error = error.message)
                        }
                }
                .onFailure { error ->
                    _local.value = _local.value.copy(error = error.message)
                }
            _local.value = _local.value.copy(isSigningInWithGoogle = false)
        }
    }
}
