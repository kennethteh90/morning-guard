package com.morningguard.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.notification.DigestNotificationManager
import com.morningguard.app.platform.NotificationAccessChecker
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import com.morningguard.data.gate.MorningGateRepository
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.domain.gate.GateEvaluation
import com.morningguard.domain.model.DigestSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val gate: GateEvaluation? = null,
    val digest: DigestSummary = DigestSummary(0, "", "", ""),
    val notificationAccessGranted: Boolean = false,
    val authState: AuthState = AuthState.Unconfigured,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    morningGateRepository: MorningGateRepository,
    private val notificationCaptureRepository: NotificationCaptureRepository,
    private val notificationAccessChecker: NotificationAccessChecker,
    authRepository: AuthRepository,
    private val digestNotificationManager: DigestNotificationManager,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        morningGateRepository.gateEvaluation,
        notificationCaptureRepository.digestSummary,
        notificationAccessChecker.isAccessGranted,
        authRepository.state,
    ) { gate, digest, access, auth ->
        HomeUiState(gate = gate, digest = digest, notificationAccessGranted = access, authState = auth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refreshAccess() {
        notificationAccessChecker.refresh()
    }

    fun clearDigest() {
        viewModelScope.launch {
            notificationCaptureRepository.clearAll()
            digestNotificationManager.clearDigest()
        }
    }
}
