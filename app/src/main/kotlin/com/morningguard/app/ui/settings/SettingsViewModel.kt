package com.morningguard.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.alarm.MorningAlarmScheduler
import com.morningguard.app.sensors.StepCounterProvider
import com.morningguard.app.debug.DebugWindowPresets
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import com.morningguard.data.backup.LocalBackupRepository
import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.data.security.EmergencyBypassRepository
import com.morningguard.data.security.NoteCryptoState
import com.morningguard.data.security.NoteEncryptionRepository
import com.morningguard.data.security.RecoveryPasswordRules
import com.morningguard.app.sync.UserDataSyncService
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.model.AlarmSettings
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningWindow
import com.morningguard.domain.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val emergencyBypassRepository: EmergencyBypassRepository,
    private val noteEncryptionRepository: NoteEncryptionRepository,
    private val bibleHighlightRepository: BibleHighlightRepository,
    private val userDataSyncService: UserDataSyncService,
    private val localBackupRepository: LocalBackupRepository,
    private val alarmScheduler: MorningAlarmScheduler,
    private val stepCounterProvider: StepCounterProvider,
) : ViewModel() {

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _accountMessage = MutableStateFlow<String?>(null)
    val accountMessage: StateFlow<String?> = _accountMessage.asStateFlow()

    private val _emergencyPassphraseMessage = MutableStateFlow<String?>(null)
    val emergencyPassphraseMessage: StateFlow<String?> = _emergencyPassphraseMessage.asStateFlow()

    private val _notesEncryptionMessage = MutableStateFlow<String?>(null)
    val notesEncryptionMessage: StateFlow<String?> = _notesEncryptionMessage.asStateFlow()

    private val _notesUnlocked = MutableStateFlow(noteEncryptionRepository.isDekUnlocked())
    val notesUnlocked: StateFlow<Boolean> = _notesUnlocked.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Unconfigured)

    val noteCryptoState: StateFlow<NoteCryptoState> = noteEncryptionRepository.cryptoState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteCryptoState())

    val hasEmergencyPassphrase: StateFlow<Boolean> = settingsRepository.goalProgressStore
        .map { store ->
            val hash = store.emergencyPassphraseHash
            !hash.isNullOrBlank()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _stepSensorAvailable = MutableStateFlow(stepCounterProvider.isAvailable)
    val stepSensorAvailable: StateFlow<Boolean> = _stepSensorAvailable.asStateFlow()

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun updateWindow(window: MorningWindow) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(morningWindow = window) }
        }
    }

    fun setWindowStart(hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = settingsRepository.currentSettings().morningWindow
            updateWindow(current.copy(startHour = hour, startMinute = minute))
        }
    }

    fun setWindowEnd(hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = settingsRepository.currentSettings().morningWindow
            updateWindow(current.copy(endHour = hour, endMinute = minute))
        }
    }

    fun debugResetTodayGoal() {
        viewModelScope.launch {
            settingsRepository.updateGoalProgress { it.copy(progress = null) }
        }
    }

    fun applyDebugWindowPreset(preset: DebugWindowPreset) {
        val window = when (preset) {
            DebugWindowPreset.IncludingNow -> DebugWindowPresets.includingNow()
            DebugWindowPreset.FullDay -> DebugWindowPresets.fullDayTest()
            DebugWindowPreset.Default -> DebugWindowPresets.defaultProduction
        }
        updateWindow(window)
    }

    fun updateAlarm(alarm: AlarmSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(alarm = alarm) }
            alarmScheduler.schedule(alarm)
        }
    }

    fun setBibleTranslation(translationId: String) {
        viewModelScope.launch {
            settingsRepository.setBibleTranslation(translationId)
        }
    }

    fun refreshStepSensorAvailability() {
        _stepSensorAvailable.value = stepCounterProvider.isAvailable
    }

    fun needsActivityRecognitionForSteps(): Boolean = stepCounterProvider.needsActivityRecognitionPermission()

    fun setGoalType(type: GoalType) {
        if (type == GoalType.STEP_COUNT && !_stepSensorAvailable.value) return
        viewModelScope.launch {
            settingsRepository.setMorningGoal(MorningGoalConfig(type = type))
        }
    }

    fun replayOnboarding() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(onboardingCompleted = false) }
        }
    }

    suspend fun exportLocalBackup(password: String, output: java.io.OutputStream) {
        runCatching {
            localBackupRepository.exportEncrypted(password.toCharArray(), output)
        }.onSuccess {
            _backupMessage.value = "Encrypted backup saved."
        }.onFailure { err ->
            _backupMessage.value = err.message ?: "Backup failed."
        }
    }

    suspend fun importLocalBackup(password: String, input: java.io.InputStream) {
        runCatching {
            localBackupRepository.importEncrypted(password.toCharArray(), input)
        }.onSuccess {
            noteEncryptionRepository.lock()
            _notesUnlocked.value = noteEncryptionRepository.isEncryptionEnabled()
            alarmScheduler.schedule(settingsRepository.currentSettings().alarm)
            authRepository.revokeRemoteSessionOnly()
            val notesEncryptionConfigured = noteEncryptionRepository.state().enabled
            _backupMessage.value = if (notesEncryptionConfigured) {
                "Backup restored. Unlock highlight notes in Settings with your notes password."
            } else {
                "Backup restored."
            }
        }.onFailure { err ->
            _backupMessage.value = err.message ?: "Restore failed."
        }
    }

    fun backupFileOpenFailed() {
        _backupMessage.value = "Could not open backup file."
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
                .onSuccess {
                    _accountMessage.value = "Signed out. Cloud sync is off until you sign in again."
                }
                .onFailure { err ->
                    _accountMessage.value = err.message ?: "Sign out failed."
                }
        }
    }

    fun saveEmergencyPassphrase(passphrase: String) {
        viewModelScope.launch {
            emergencyBypassRepository.setPassphrase(passphrase)
                .onSuccess {
                    _emergencyPassphraseMessage.value = "Emergency passphrase updated."
                }
                .onFailure { err ->
                    _emergencyPassphraseMessage.value = err.message
                }
        }
    }

    fun clearAccountMessage() {
        _accountMessage.value = null
    }

    fun clearEmergencyPassphraseMessage() {
        _emergencyPassphraseMessage.value = null
    }

    fun setupNotesEncryption(password: String, confirmation: String) {
        viewModelScope.launch {
            RecoveryPasswordRules.validate(password.toCharArray())?.let {
                _notesEncryptionMessage.value = it
                return@launch
            }
            if (password != confirmation) {
                _notesEncryptionMessage.value = "Passwords do not match."
                return@launch
            }
            noteEncryptionRepository.ensureDekReady().onFailure { err ->
                _notesEncryptionMessage.value = err.message
                return@launch
            }
            noteEncryptionRepository.setRecoveryPassword(password.toCharArray())
                .onSuccess {
                    bibleHighlightRepository.reencryptAllStoredNotes()
                    userDataSyncService.syncAllIfSignedIn()
                    _notesUnlocked.value = true
                    _notesEncryptionMessage.value =
                        "Recovery password saved. Store it in a password manager to open notes on a new phone."
                }
                .onFailure { err ->
                    _notesEncryptionMessage.value = err.message
                }
        }
    }

    fun unlockNotesEncryption(password: String) {
        viewModelScope.launch {
            noteEncryptionRepository.unlockWithPassword(password.toCharArray())
                .onSuccess {
                    _notesUnlocked.value = true
                    _notesEncryptionMessage.value = "Notes unlocked on this device."
                }
                .onFailure { err ->
                    _notesEncryptionMessage.value = err.message
                }
        }
    }

    fun changeNotesEncryptionPassword(current: String, newPassword: String, confirmation: String) {
        viewModelScope.launch {
            RecoveryPasswordRules.validate(newPassword.toCharArray())?.let {
                _notesEncryptionMessage.value = it
                return@launch
            }
            if (newPassword != confirmation) {
                _notesEncryptionMessage.value = "Passwords do not match."
                return@launch
            }
            noteEncryptionRepository.changePassword(current.toCharArray(), newPassword.toCharArray())
                .onSuccess {
                    userDataSyncService.syncAllIfSignedIn()
                    _notesEncryptionMessage.value = "Notes password updated."
                }
                .onFailure { err ->
                    _notesEncryptionMessage.value = err.message
                }
        }
    }

    fun refreshNotesUnlocked() {
        _notesUnlocked.value = noteEncryptionRepository.isDekUnlocked()
    }
}

enum class DebugWindowPreset {
    IncludingNow,
    FullDay,
    Default,
}
