package com.morningguard.app.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.sync.UserDataSyncService
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.data.security.NoteEncryptionRepository
import com.morningguard.data.security.RecoveryPasswordPromptPolicy
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.model.DataStorageMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecoveryPasswordUiState(
    val showDedicatedSheet: Boolean = false,
    val showBlockingCloudRecovery: Boolean = false,
    val showBanner: Boolean = false,
    val hasRecoveryPassword: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class RecoveryPasswordViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val noteEncryptionRepository: NoteEncryptionRepository,
    private val bibleHighlightRepository: BibleHighlightRepository,
    private val userDataSyncService: UserDataSyncService,
) : ViewModel() {

    private val sheetRequest = MutableStateFlow(false)
    private val periodicRequest = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            authRepository.state.collect { auth ->
                if (auth is AuthState.SignedIn) {
                    onSignedIn()
                }
            }
        }
    }

    val uiState: StateFlow<RecoveryPasswordUiState> = combine(
        settingsRepository.settings,
        authRepository.state,
        noteEncryptionRepository.cryptoState,
        sheetRequest,
        periodicRequest,
    ) { settings, auth, crypto, sheet, periodic ->
        val signedIn = auth is AuthState.SignedIn
        val cloudSync = settings.dataStorageMode == DataStorageMode.CLOUD_SYNC
        val hasRecovery = crypto.hasRecoveryPassword()
        val cloudRecovery = crypto.hasPendingCloudRecovery()
        val dedicated = sheet || periodic ||
            RecoveryPasswordPromptPolicy.shouldShowDedicatedSheet(
                settings,
                hasRecovery,
                signedIn,
                cloudRecovery,
            ) ||
            RecoveryPasswordPromptPolicy.shouldShowPeriodicSheet(settings, hasRecovery, signedIn)
        RecoveryPasswordUiState(
            showDedicatedSheet = dedicated && !cloudRecovery,
            showBlockingCloudRecovery = cloudRecovery && signedIn && cloudSync,
            showBanner = RecoveryPasswordPromptPolicy.shouldShowBanner(settings, hasRecovery, signedIn),
            hasRecoveryPassword = hasRecovery,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecoveryPasswordUiState())

    fun onSignedIn() {
        viewModelScope.launch {
            noteEncryptionRepository.ensureDekReady()
            sheetRequest.value = true
        }
    }

    fun onNoteSaved() {
        viewModelScope.launch {
            settingsRepository.incrementNoteSaveCountForRecoveryPrompt()
            if (RecoveryPasswordPromptPolicy.shouldShowPeriodicSheet(
                    settingsRepository.currentSettings(),
                    noteEncryptionRepository.hasRecoveryPassword(),
                    authRepository.state.value is AuthState.SignedIn,
                )
            ) {
                periodicRequest.value = true
            }
        }
    }

    fun dismissLater() {
        viewModelScope.launch {
            settingsRepository.recordRecoveryPasswordPromptDismissed()
            sheetRequest.value = false
            periodicRequest.value = false
        }
    }

    fun markPromptShown() {
        viewModelScope.launch {
            settingsRepository.markRecoveryPasswordPromptShownAfterSignIn()
        }
    }

    fun setRecoveryPassword(password: CharArray, confirmation: CharArray) {
        viewModelScope.launch {
            if (password.size < 8) {
                _error.value = "Use at least 8 characters."
                return@launch
            }
            if (!password.contentEquals(confirmation)) {
                _error.value = "Passwords do not match."
                return@launch
            }
            noteEncryptionRepository.setRecoveryPassword(password)
                .onSuccess {
                    bibleHighlightRepository.reencryptAllStoredNotes()
                    userDataSyncService.syncAllIfSignedIn()
                    sheetRequest.value = false
                    periodicRequest.value = false
                    _status.value = "Recovery password saved. Store it in a password manager."
                    _error.value = null
                }
                .onFailure { err ->
                    _error.value = err.message
                }
        }
    }

    fun unlockCloudRecovery(password: CharArray) {
        viewModelScope.launch {
            noteEncryptionRepository.unlockWithPassword(password)
                .onSuccess {
                    bibleHighlightRepository.reencryptAllStoredNotes()
                    userDataSyncService.syncAllIfSignedIn()
                    _status.value = "Notes unlocked on this device."
                    _error.value = null
                }
                .onFailure { err ->
                    _error.value = err.message ?: "Incorrect recovery password."
                }
        }
    }

    private val _status = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    fun clearMessages() {
        _status.value = null
        _error.value = null
    }
}
