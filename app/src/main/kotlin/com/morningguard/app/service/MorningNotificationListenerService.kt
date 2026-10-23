package com.morningguard.app.service

import android.service.notification.StatusBarNotification
import com.morningguard.app.gate.GateStateHolder
import com.morningguard.app.notification.DigestNotificationManager
import com.morningguard.app.notification.NotificationExtrasReader
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.domain.model.CapturedNotification
import com.morningguard.domain.notification.KnownAppLabels
import com.morningguard.domain.notification.NotificationPolicy
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MorningNotificationListenerService : android.service.notification.NotificationListenerService() {

    private lateinit var captureRepository: NotificationCaptureRepository
    private lateinit var digestNotificationManager: DigestNotificationManager
    private lateinit var gateStateHolder: GateStateHolder
    private lateinit var analyticsTracker: AnalyticsTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationListenerEntryPoint::class.java,
        )
        captureRepository = entryPoint.captureRepository()
        digestNotificationManager = entryPoint.digestNotificationManager()
        gateStateHolder = entryPoint.gateStateHolder()
        analyticsTracker = entryPoint.analyticsTracker()
        scope.launch { gateStateHolder.refreshNow() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch {
            gateStateHolder.refreshNow()
            captureRepository.snapshotDigestSummary().let { digestNotificationManager.showDigest(it) }
            captureRepository.digestSummary.collect { summary ->
                digestNotificationManager.showDigest(summary)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runBlocking(Dispatchers.IO) {
            gateStateHolder.refreshNow()
        }

        val evaluation = gateStateHolder.latestEvaluation
        val settings = gateStateHolder.latestSettings
        val extras = sbn.notification.extras
        val (rawTitle, text) = NotificationExtrasReader.readTitleAndText(extras)
        val title = normalizeCaptureTitle(sbn.packageName, rawTitle, text)
        val category = sbn.notification.category

        val shouldIntercept = NotificationPolicy.shouldIntercept(
            packageName = sbn.packageName,
            category = category,
            bypassPackages = settings.bypassPackageNames,
            gateState = evaluation.state,
            digestOnlyDuringMorningGate = settings.digestOnlyDuringMorningGate,
            appPackageName = packageName,
        )

        if (!shouldIntercept) return

        scope.launch {
            captureRepository.capture(
                CapturedNotification(
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    postedAtEpochMs = sbn.postTime,
                    category = category,
                    notificationKey = sbn.key,
                ),
            )
            analyticsTracker.track(
                "notification_captured",
                mapOf("package" to sbn.packageName),
            )
            digestNotificationManager.showDigest(captureRepository.snapshotDigestSummary())
        }

        cancelNotification(sbn.key)
    }

    private fun normalizeCaptureTitle(packageName: String, title: String, text: String): String {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty() && trimmed != packageName) return trimmed
        val fromText = text.lineSequence().firstOrNull()?.trim().orEmpty()
        if (fromText.isNotEmpty() && fromText.length <= 120) return fromText
        return KnownAppLabels.labelFor(packageName)?.let { "$it notification" } ?: "Notification"
    }
}
