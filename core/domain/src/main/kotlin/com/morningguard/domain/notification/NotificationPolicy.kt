package com.morningguard.domain.notification

import com.morningguard.domain.gate.GateState

object NotificationPolicy {
    const val ALARM_CATEGORY = "alarm"

    fun shouldIntercept(
        packageName: String,
        category: String?,
        bypassPackages: Set<String>,
        gateState: GateState,
        digestOnlyDuringMorningGate: Boolean,
        appPackageName: String,
    ): Boolean {
        if (packageName == appPackageName) return false
        if (category == ALARM_CATEGORY) return false
        if (packageName in bypassPackages) return false

        return if (digestOnlyDuringMorningGate) {
            gateState == GateState.LOCKED
        } else {
            true
        }
    }
}
