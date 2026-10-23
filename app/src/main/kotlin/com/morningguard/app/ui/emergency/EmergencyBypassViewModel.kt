package com.morningguard.app.ui.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.sync.UserDataSyncService
import com.morningguard.data.security.EmergencyBypassRepository
import com.morningguard.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmergencyUiState(
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class EmergencyBypassViewModel @Inject constructor(
    private val emergencyBypassRepository: EmergencyBypassRepository,
    private val settingsRepository: SettingsRepository,
    private val userDataSyncService: UserDataSyncService,
) : ViewModel() {

    private val _state = MutableStateFlow(EmergencyUiState())
    val state: StateFlow<EmergencyUiState> = _state.asStateFlow()

    val hasPassphrase: StateFlow<Boolean> = settingsRepository.goalProgressStore
        .map { !it.emergencyPassphraseHash.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun attemptBypass(passphrase: String) {
        viewModelScope.launch {
            emergencyBypassRepository.attemptBypass(passphrase)
                .onSuccess {
                    userDataSyncService.syncMorningHistoryIfSignedIn()
                        .onFailure { /* logged in UserDataSyncService */ }
                    _state.value = EmergencyUiState(message = "Bypass active for 1 hour")
                }
                .onFailure { _state.value = EmergencyUiState(error = it.message) }
        }
    }
}
