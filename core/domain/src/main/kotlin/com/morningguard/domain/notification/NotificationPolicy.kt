package com.morningguard.domain.notification

import com.morningguard.domain.gate.GateState

object NotificationPolicy {
    const val ALARM_CATEGORY = "alarm"
    const val CALL_CATEGORY = "call"

    val neverInterceptCategories: Set<String> = setOf(ALARM_CATEGORY, CALL_CATEGORY)

    val neverInterceptPackages: Set<String> = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.server.telecom",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.android.messaging",
        "com.samsung.android.messaging",
    )

    fun shouldIntercept(
        packageName: String,
        category: String?,
        bypassPackages: Set<String>,
        gateState: GateState,
        digestOnlyDuringMorningGate: Boolean,
        appPackageName: String,
    ): Boolean {
        if (packageName == appPackageName) return false
        if (category in neverInterceptCategories) return false
        if (packageName in neverInterceptPackages) return false
        if (packageName in bypassPackages) return false

        return if (digestOnlyDuringMorningGate) {
            gateState == GateState.LOCKED
        } else {
            true
        }
    }
}
