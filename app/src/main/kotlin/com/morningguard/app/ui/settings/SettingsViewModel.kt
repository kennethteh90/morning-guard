package com.morningguard.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.alarm.MorningAlarmScheduler
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.model.AlarmSettings
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningWindow
import com.morningguard.domain.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: MorningAlarmScheduler,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun updateWindow(window: MorningWindow) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(morningWindow = window) }
        }
    }

    fun updateAlarm(alarm: AlarmSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(alarm = alarm) }
            alarmScheduler.schedule(alarm)
        }
    }

    fun setGoalType(type: GoalType) {
        viewModelScope.launch {
            settingsRepository.setMorningGoal(MorningGoalConfig(type = type))
        }
    }

    fun toggleDigestOnlyDuringGate(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(digestOnlyDuringMorningGate = enabled) }
        }
    }
}
