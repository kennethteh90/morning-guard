package com.morningguard.app.service

import com.morningguard.domain.gate.GateEvaluation
import com.morningguard.domain.gate.GateState
import com.morningguard.domain.model.UserSettings
import com.morningguard.domain.notification.NotificationPolicy

/**
 * Pure decision logic for [MorningNotificationListenerService] — unit-tested without a bound service.
 */
internal object NotificationListenerPostHandler {

    data class PostAction(
        val shouldIntercept: Boolean,
        /** Diagnostic code when [shouldIntercept] is false. */
        val skipReason: String? = null,
    )

    fun evaluatePost(
        packageName: String,
        category: String?,
        settings: UserSettings,
        evaluation: GateEvaluation,
        appPackageName: String,
    ): PostAction {
        val intercept = NotificationPolicy.shouldIntercept(
            packageName = packageName,
            category = category,
            bypassPackages = settings.bypassPackageNames,
            gateState = evaluation.state,
            digestOnlyDuringMorningGate = settings.digestOnlyDuringMorningGate,
            appPackageName = appPackageName,
        )
        if (intercept) {
            return PostAction(shouldIntercept = true)
        }
        return PostAction(
            shouldIntercept = false,
            skipReason = skipReason(packageName, category, settings, evaluation, appPackageName),
        )
    }

    /** Only remove from the shade after a row is persisted (or upserted) in the digest store. */
    fun shouldCancelAfterCapture(captureRowId: Long?): Boolean = captureRowId != null

    private fun skipReason(
        packageName: String,
        category: String?,
        settings: UserSettings,
        evaluation: GateEvaluation,
        appPackageName: String,
    ): String {
        if (packageName == appPackageName) return "self"
        if (category in NotificationPolicy.neverInterceptCategories) return "category_$category"
        if (packageName in NotificationPolicy.neverInterceptPackages) return "sms_or_dialer"
        if (packageName in settings.bypassPackageNames) return "bypass"
        if (settings.digestOnlyDuringMorningGate && evaluation.state != GateState.LOCKED) {
            return "gate_${evaluation.state.name.lowercase()}_reason_${evaluation.reason}"
        }
        return "policy"
    }
}
