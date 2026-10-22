package com.morningguard.app.platform

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.morningguard.app.service.MorningNotificationListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationListenerRegistration(
    val componentFlattened: String,
    val registeredWithPackageManager: Boolean,
)

@Singleton
class NotificationListenerRegistrationProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun probe(): NotificationListenerRegistration {
        val component = ComponentName(context, MorningNotificationListenerService::class.java)
        return NotificationListenerRegistration(
            componentFlattened = component.flattenToString(),
            registeredWithPackageManager = isRegisteredWithPackageManager(),
        )
    }

    fun isRegisteredWithPackageManager(): Boolean {
        val intent = Intent(NotificationListenerService.SERVICE_INTERFACE).apply {
            setPackage(context.packageName)
        }
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
        val services = context.packageManager.queryIntentServices(intent, flags)
        val expectedName = MorningNotificationListenerService::class.java.name
        return services.any { resolve ->
            resolve.serviceInfo.name == expectedName &&
                resolve.serviceInfo.permission == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE
        }
    }

    fun openAccessSettings(context: Context) {
        val component = ComponentName(context, MorningNotificationListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val detail = Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS").apply {
                putExtra(
                    "android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME",
                    component.flattenToString(),
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (detail.resolveActivity(context.packageManager) != null) {
                context.startActivity(detail)
                return
            }
        }
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
