package com.morningguard.app.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService

enum class AlarmPermissionKind {
    ExactAlarm,
    FullScreenIntent,
}

object AlarmPermissions {
    fun missing(context: Context): List<AlarmPermissionKind> {
        val missing = mutableListOf<AlarmPermissionKind>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService<AlarmManager>()
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                missing.add(AlarmPermissionKind.ExactAlarm)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService<NotificationManager>()
            if (notificationManager != null && !notificationManager.canUseFullScreenIntent()) {
                missing.add(AlarmPermissionKind.FullScreenIntent)
            }
        }
        return missing
    }

    fun openSettings(context: Context, kind: AlarmPermissionKind) {
        when (kind) {
            AlarmPermissionKind.ExactAlarm -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
            AlarmPermissionKind.FullScreenIntent -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
        }
    }
}
