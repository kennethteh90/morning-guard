package com.morningguard.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppRoute {
    Loading,
    Onboarding,
    Main,
}

@HiltViewModel
class AppRouteViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val route: StateFlow<AppRoute> = settingsRepository.settings
        .map { settings ->
            if (settings.onboardingCompleted) AppRoute.Main else AppRoute.Onboarding
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppRoute.Onboarding)

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(onboardingCompleted = true) }
        }
    }

    fun resetOnboardingForDebug() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(onboardingCompleted = false) }
        }
    }
}
