package com.morningguard.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val authState: AuthState = AuthState.Unconfigured,
    val statusMessage: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _local = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _local.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Unconfigured)

    fun sendMagicLink(email: String) {
        viewModelScope.launch {
            authRepository.sendMagicLink(email)
                .onSuccess {
                    _local.value = AuthUiState(
                        authState = authRepository.state.value,
                        statusMessage = "Check your email for a magic link.",
                    )
                }
                .onFailure {
                    _local.value = AuthUiState(error = it.message)
                }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
