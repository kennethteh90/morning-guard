package com.morningguard.app.service

import android.service.notification.StatusBarNotification
import com.morningguard.app.gate.GateStateHolder
import com.morningguard.app.notification.DigestNotificationManager
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.domain.model.CapturedNotification
import com.morningguard.domain.notification.NotificationPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MorningNotificationListenerService : android.service.notification.NotificationListenerService() {

    @Inject lateinit var captureRepository: NotificationCaptureRepository
    @Inject lateinit var digestNotificationManager: DigestNotificationManager
    @Inject lateinit var gateStateHolder: GateStateHolder
    @Inject lateinit var analyticsTracker: AnalyticsTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch {
            captureRepository.digestSummary.collect { summary ->
                digestNotificationManager.showDigest(summary)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val category = sbn.notification.category

        val shouldIntercept = NotificationPolicy.shouldIntercept(
            packageName = sbn.packageName,
            category = category,
            bypassPackages = gateStateHolder.latestSettings.bypassPackageNames,
            gateState = gateStateHolder.latestEvaluation.state,
            digestOnlyDuringMorningGate = gateStateHolder.latestSettings.digestOnlyDuringMorningGate,
            appPackageName = packageName,
        )

        if (!shouldIntercept) return

        scope.launch {
            captureRepository.capture(
                CapturedNotification(
                    packageName = sbn.packageName,
                    title = title.ifBlank { sbn.packageName },
                    text = text,
                    postedAtEpochMs = sbn.postTime,
                    category = category,
                ),
            )
            analyticsTracker.track(
                "notification_captured",
                mapOf("package" to sbn.packageName),
            )
        }

        cancelNotification(sbn.key)
    }
}
