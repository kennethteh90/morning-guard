package com.morningguard.app.ui.bypass

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstalledApp(val packageName: String, val label: String)

@HiltViewModel
class BypassAppsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val pm = context.packageManager

    val installedApps: List<InstalledApp> = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName.contains("android") }
        .map { app ->
            InstalledApp(
                packageName = app.packageName,
                label = pm.getApplicationLabel(app).toString(),
            )
        }
        .sortedBy { it.label.lowercase() }

    val bypassPackages: StateFlow<Set<String>> = settingsRepository.settings
        .map { it.bypassPackageNames }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggle(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.currentSettings().bypassPackageNames
            val updated = if (enabled) current + packageName else current - packageName
            settingsRepository.setBypassPackages(updated)
        }
    }
}
