package com.morningguard.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.auth.RecentAuthUiEvents
import com.morningguard.app.platform.NotificationAccessChecker
import com.morningguard.app.platform.NotificationListenerRegistration
import com.morningguard.app.platform.NotificationListenerRegistrationProbe
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import com.morningguard.data.gate.MorningGateRepository
import com.morningguard.data.gate.PeaceSessionRepository
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.domain.gate.GateEvaluation
import com.morningguard.domain.model.DigestSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val gate: GateEvaluation? = null,
    val digest: DigestSummary = DigestSummary(0, "", "", ""),
    val notificationAccessGranted: Boolean = false,
    val listenerRegistration: NotificationListenerRegistration? = null,
    val authState: AuthState = AuthState.Unconfigured,
    val signedInEmail: String? = null,
    val peaceSessionUntilEpochMs: Long? = null,
    val morningGoalCompleteToday: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    morningGateRepository: MorningGateRepository,
    private val notificationCaptureRepository: NotificationCaptureRepository,
    private val notificationAccessChecker: NotificationAccessChecker,
    private val listenerProbe: NotificationListenerRegistrationProbe,
    private val authRepository: AuthRepository,
    private val recentAuthUiEvents: RecentAuthUiEvents,
    private val peaceSessionRepository: PeaceSessionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _magicLinkSignInMessage = MutableStateFlow<String?>(null)
    val magicLinkSignInMessage: StateFlow<String?> = _magicLinkSignInMessage.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        morningGateRepository.gateEvaluation,
        notificationCaptureRepository.digestSummary,
        notificationAccessChecker.isAccessGranted,
        authRepository.state,
        settingsRepository.goalProgressStore,
    ) { gate, digest, access, auth, store ->
        val now = System.currentTimeMillis()
        val peaceUntil = store.peaceSessionUntilEpochMs?.takeIf { it > now }
        val dateKey = java.time.LocalDate.now().toString()
        val progress = store.progress
        val goalCompleteToday = progress?.dateKey == dateKey && progress.isComplete
        HomeUiState(
            gate = gate,
            digest = digest,
            notificationAccessGranted = access,
            listenerRegistration = listenerProbe.probe(),
            authState = auth,
            signedInEmail = (auth as? AuthState.SignedIn)?.user?.email,
            peaceSessionUntilEpochMs = peaceUntil,
            morningGoalCompleteToday = goalCompleteToday,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun startOrExtendPeaceSession() {
        viewModelScope.launch {
            peaceSessionRepository.startOrExtendOneHour()
        }
    }

    fun endPeaceSession() {
        viewModelScope.launch {
            peaceSessionRepository.endPeaceSession()
        }
    }

    fun onHomeResumed() {
        viewModelScope.launch {
            peaceSessionRepository.reconcileStoredPeaceSession()
        }
        notificationAccessChecker.refresh()
        notificationAccessChecker.rebindIfEnabled()
        if (recentAuthUiEvents.consumeMagicLinkCompleted()) {
            val email = (authRepository.state.value as? AuthState.SignedIn)?.user?.email
            _magicLinkSignInMessage.update {
                if (email.isNullOrBlank()) {
                    "You're signed in — cloud sync is on."
                } else {
                    "Signed in as $email"
                }
            }
        }
    }

    fun clearMagicLinkSignInMessage() {
        _magicLinkSignInMessage.value = null
    }

    fun openNotificationAccess(context: android.content.Context) {
        listenerProbe.openAccessSettings(context)
    }

}
