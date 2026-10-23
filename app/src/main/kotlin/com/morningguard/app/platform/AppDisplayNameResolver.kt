package com.morningguard.app.platform

import android.content.Context
import android.content.pm.PackageManager
import com.morningguard.domain.notification.KnownAppLabels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDisplayNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun labelFor(packageName: String): String {
        KnownAppLabels.labelFor(packageName)?.let { return it }
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
                .takeIf { it.isNotBlank() && it != packageName }
                ?: packageName
        } catch (_: PackageManager.NameNotFoundException) {
            KnownAppLabels.labelFor(packageName) ?: packageName
        }
    }
}
