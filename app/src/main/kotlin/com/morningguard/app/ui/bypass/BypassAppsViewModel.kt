package com.morningguard.app.ui.bypass

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.notification.KnownAppLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val seenInNotifications: Boolean = false,
)

@HiltViewModel
class BypassAppsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
    private val notificationCaptureRepository: NotificationCaptureRepository,
) : ViewModel() {

    private val pm = context.packageManager

    private val _installedApps = MutableStateFlow(buildInstalledApps(emptySet()))
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    init {
        viewModelScope.launch {
            val notifying = notificationCaptureRepository.distinctNotifyingPackageNames().toSet()
            _installedApps.value = buildInstalledApps(notifying)
        }
    }

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

    private fun buildInstalledApps(notifying: Set<String>): List<InstalledApp> {
        val packages = launcherPackages() + notifying
        return packages
            .mapNotNull { packageName -> toInstalledApp(packageName, notifying) }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<InstalledApp> { !it.seenInNotifications }
                    .thenBy { it.label.lowercase() },
            )
    }

    private fun toInstalledApp(packageName: String, notifying: Set<String>): InstalledApp? {
        val seenInNotifications = packageName in notifying
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        if (appInfo != null) {
            val pmLabel = pm.getApplicationLabel(appInfo).toString().trim()
            val label = pmLabel.takeIf { it.isNotBlank() && it != packageName }
                ?: KnownAppLabels.labelFor(packageName)
                ?: packageName
            return InstalledApp(
                packageName = packageName,
                label = label,
                isSystemApp = isSystemApp(appInfo),
                seenInNotifications = seenInNotifications,
            )
        }
        if (!seenInNotifications) return null
        return InstalledApp(
            packageName = packageName,
            label = KnownAppLabels.labelFor(packageName) ?: packageName,
            isSystemApp = false,
            seenInNotifications = true,
        )
    }

    private fun launcherPackages(): Set<String> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private fun isSystemApp(info: ApplicationInfo): Boolean {
        val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val updatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return system && !updatedSystem
    }
}
