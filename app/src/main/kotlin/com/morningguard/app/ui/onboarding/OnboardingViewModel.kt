package com.morningguard.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.platform.PresenterMode
import com.morningguard.app.alarm.MorningAlarmScheduler
import com.morningguard.app.screenshot.ScreenshotOnboardingPrefs
import com.morningguard.app.sensors.StepCounterProvider
import com.morningguard.app.platform.NotificationAccessChecker
import com.morningguard.app.platform.NotificationListenerRegistrationProbe
import android.util.Patterns
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import com.morningguard.data.backup.LocalBackupRepository
import com.morningguard.data.security.EmergencyBypassRepository
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.model.DataStorageMode
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep {
    Welcome,
    HowItWorks,
    YourData,
    PickGoal,
    MorningWindow,
    NotificationAccess,
    EmergencyPassphrase,
    AllSet,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val selectedGoal: GoalType = GoalType.BIBLE_CHAPTER,
    val morningWindow: MorningWindow = MorningWindow(),
    val notificationAccessGranted: Boolean = false,
    val listenerRegistered: Boolean = false,
    val emergencyPassphrase: String = "",
    val errorMessage: String? = null,
    val stepSensorAvailable: Boolean = true,
    val notificationDisclosureAccepted: Boolean = false,
    val syncEmail: String = "",
    val magicLinkSent: Boolean = false,
    val magicLinkStatus: String? = null,
    val magicLinkError: String? = null,
    val isSendingMagicLink: Boolean = false,
    val choseLocalOnly: Boolean = false,
    val backupMessage: String? = null,
    val signedInForOnboarding: Boolean = false,
    val signedInEmail: String? = null,
) {
    val stepIndex: Int get() = step.ordinal
    val stepCount: Int get() = OnboardingStep.entries.size
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val localBackupRepository: LocalBackupRepository,
    private val emergencyBypassRepository: EmergencyBypassRepository,
    private val accessChecker: NotificationAccessChecker,
    private val listenerProbe: NotificationListenerRegistrationProbe,
    private val alarmScheduler: MorningAlarmScheduler,
    private val stepCounterProvider: StepCounterProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OnboardingUiState(
            step = if (PresenterMode.enabled) {
                ScreenshotOnboardingPrefs.initialStep ?: OnboardingStep.Welcome
            } else {
                OnboardingStep.Welcome
            },
            notificationDisclosureAccepted = PresenterMode.enabled &&
                ScreenshotOnboardingPrefs.disclosureAccepted,
            stepSensorAvailable = stepCounterProvider.isAvailable,
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.state.collect { auth ->
                _state.update {
                    it.copy(
                        signedInForOnboarding = auth is AuthState.SignedIn,
                        signedInEmail = (auth as? AuthState.SignedIn)?.user?.email,
                    )
                }
            }
        }
    }

    fun onResume() {
        accessChecker.refresh()
        accessChecker.rebindIfEnabled()
        _state.update {
            it.copy(
                notificationAccessGranted = accessChecker.isAccessGranted.value,
                listenerRegistered = listenerProbe.isRegisteredWithPackageManager(),
                stepSensorAvailable = stepCounterProvider.isAvailable,
            )
        }
    }

    fun refreshStepSensorAvailability() {
        _state.update { it.copy(stepSensorAvailable = stepCounterProvider.isAvailable) }
    }

    fun needsActivityRecognitionForSteps(): Boolean = stepCounterProvider.needsActivityRecognitionPermission()

    fun next() {
        _state.update { current ->
            val nextStep = OnboardingStep.entries.getOrNull(current.stepIndex + 1) ?: OnboardingStep.AllSet
            current.copy(step = nextStep, errorMessage = null)
        }
        viewModelScope.launch { persistPartialSettings() }
    }

    fun back() {
        _state.update { current ->
            val prev = OnboardingStep.entries.getOrNull(current.stepIndex - 1) ?: OnboardingStep.Welcome
            current.copy(step = prev, errorMessage = null)
        }
    }

    fun selectGoal(type: GoalType) {
        if (type == GoalType.STEP_COUNT && !stepCounterProvider.isAvailable) return
        _state.update { it.copy(selectedGoal = type) }
    }

    fun selectWindowPreset(preset: WindowPreset) {
        _state.update { it.copy(morningWindow = preset.window) }
    }

    fun setEmergencyPassphrase(value: String) {
        _state.update { it.copy(emergencyPassphrase = value) }
    }

    fun setNotificationDisclosureAccepted(accepted: Boolean) {
        _state.update { it.copy(notificationDisclosureAccepted = accepted) }
    }

    fun setSyncEmail(value: String) {
        _state.update { it.copy(syncEmail = value, magicLinkError = null) }
    }

    fun continueWithLocalData() {
        viewModelScope.launch {
            if (authRepository.state.value is AuthState.SignedIn) {
                authRepository.signOut()
            }
            settingsRepository.updateSettings {
                it.copy(dataStorageMode = DataStorageMode.LOCAL_ONLY)
            }
            _state.update {
                it.copy(
                    choseLocalOnly = true,
                    magicLinkError = null,
                    magicLinkStatus = null,
                )
            }
            next()
        }
    }

    fun continueWithCloudSync() {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(dataStorageMode = DataStorageMode.CLOUD_SYNC)
            }
            _state.update {
                it.copy(
                    choseLocalOnly = false,
                    magicLinkError = null,
                )
            }
            next()
        }
    }

    fun sendMagicLinkAndContinue() {
        val normalized = _state.value.syncEmail.trim()
        if (normalized.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            _state.update { it.copy(magicLinkError = "Enter a valid email address.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSendingMagicLink = true, magicLinkError = null) }
            authRepository.sendMagicLink(normalized)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSendingMagicLink = false,
                            magicLinkSent = true,
                            magicLinkStatus = "Link sent to $normalized. Check your inbox and open it on this phone when it arrives — you can finish setup now.",
                            magicLinkError = null,
                        )
                    }
                    next()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isSendingMagicLink = false,
                            magicLinkError = err.message ?: "Could not send magic link.",
                        )
                    }
                }
        }
    }

    fun saveEmergencyAndContinue() {
        viewModelScope.launch {
            val phrase = _state.value.emergencyPassphrase
            if (phrase.isNotBlank()) {
                emergencyBypassRepository.setPassphrase(phrase)
                    .onFailure { err ->
                        _state.update { it.copy(errorMessage = err.message) }
                        return@launch
                    }
            }
            next()
        }
    }

    fun finish() {
        viewModelScope.launch {
            persistPartialSettings()
            val alarm = settingsRepository.currentSettings().alarm
            alarmScheduler.schedule(alarm)
            settingsRepository.updateSettings { it.copy(onboardingCompleted = true) }
        }
    }

    suspend fun exportLocalBackup(password: String, output: java.io.OutputStream) {
        runCatching {
            localBackupRepository.exportEncrypted(password.toCharArray(), output)
        }.onSuccess {
            _state.update { it.copy(backupMessage = "Encrypted backup saved.") }
        }.onFailure { err ->
            _state.update { it.copy(backupMessage = err.message ?: "Backup failed.") }
        }
    }

    fun backupFileOpenFailed() {
        _state.update { it.copy(backupMessage = "Could not open backup file.") }
    }

    fun clearBackupMessage() {
        _state.update { it.copy(backupMessage = null) }
    }

    private suspend fun persistPartialSettings() {
        val s = _state.value
        settingsRepository.updateSettings { settings ->
            settings.copy(
                morningGoal = MorningGoalConfig(type = s.selectedGoal),
                morningWindow = s.morningWindow,
            )
        }
    }
}

enum class WindowPreset(val label: String, val description: String, val window: MorningWindow) {
    DefaultHold("Overnight hold", "1:00 am – 10:00 am", MorningWindow(1, 0, 10, 0)),
    EarlyBird("Early bird", "5:00 am – 10:00 am", MorningWindow(5, 0, 10, 0)),
    Standard("Standard", "6:00 am – 11:00 am", MorningWindow(6, 0, 11, 0)),
    Late("Late start", "7:00 am – 12:00 pm", MorningWindow(7, 0, 12, 0)),
}
