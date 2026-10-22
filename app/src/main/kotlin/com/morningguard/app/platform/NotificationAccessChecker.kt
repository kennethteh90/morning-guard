package com.morningguard.app.platform

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.morningguard.app.service.MorningNotificationListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _isAccessGranted = MutableStateFlow(check())
    val isAccessGranted: StateFlow<Boolean> = _isAccessGranted.asStateFlow()

    fun refresh() {
        _isAccessGranted.value = check()
    }

    fun rebindIfEnabled() {
        if (!check()) return
        val component = ComponentName(context, MorningNotificationListenerService::class.java)
        NotificationListenerService.requestRebind(component)
    }

    private fun check(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val component = ComponentName(context, MorningNotificationListenerService::class.java)
        val flattened = component.flattenToString()
        val serviceSuffix = MorningNotificationListenerService::class.java.name
        return flat.split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { entry ->
                entry.equals(flattened, ignoreCase = true) ||
                    entry.endsWith(serviceSuffix, ignoreCase = true)
            }
    }
}
