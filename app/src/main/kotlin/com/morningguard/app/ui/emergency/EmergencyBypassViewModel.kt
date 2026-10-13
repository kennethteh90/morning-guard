package com.morningguard.app.ui.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.data.security.EmergencyBypassRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmergencyUiState(
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class EmergencyBypassViewModel @Inject constructor(
    private val emergencyBypassRepository: EmergencyBypassRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EmergencyUiState())
    val state: StateFlow<EmergencyUiState> = _state.asStateFlow()

    fun savePassphrase(passphrase: String) {
        viewModelScope.launch {
            emergencyBypassRepository.setPassphrase(passphrase)
                .onSuccess { _state.value = EmergencyUiState(message = "Passphrase saved") }
                .onFailure { _state.value = EmergencyUiState(error = it.message) }
        }
    }

    fun attemptBypass(passphrase: String) {
        viewModelScope.launch {
            emergencyBypassRepository.attemptBypass(passphrase)
                .onSuccess {
                    _state.value = EmergencyUiState(message = "Bypass active for 1 hour")
                }
                .onFailure { _state.value = EmergencyUiState(error = it.message) }
        }
    }
}
