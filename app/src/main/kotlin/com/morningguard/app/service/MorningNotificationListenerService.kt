package com.morningguard.app.service

import android.service.notification.StatusBarNotification
import com.morningguard.app.gate.GateStateHolder
import com.morningguard.app.notification.DigestNotificationManager
import com.morningguard.app.notification.NotificationExtrasReader
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.monitoring.ErrorReporter
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.domain.model.CapturedNotification
import com.morningguard.domain.notification.KnownAppLabels
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MorningNotificationListenerService : android.service.notification.NotificationListenerService() {

    private lateinit var captureRepository: NotificationCaptureRepository
    private lateinit var digestNotificationManager: DigestNotificationManager
    private lateinit var gateStateHolder: GateStateHolder
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var errorReporter: ErrorReporter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var digestCollectJob: Job? = null

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
        errorReporter = entryPoint.errorReporter()
        scope.launch { gateStateHolder.refreshNow() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        digestCollectJob?.cancel()
        digestCollectJob = scope.launch {
            gateStateHolder.refreshNow()
            captureRepository.snapshotDigestSummary().let { digestNotificationManager.showDigest(it) }
            captureRepository.digestSummary.collect { summary ->
                digestNotificationManager.showDigest(summary)
            }
        }
    }

    override fun onListenerDisconnected() {
        digestCollectJob?.cancel()
        digestCollectJob = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scope.launch {
            handleNotificationPosted(sbn)
        }
    }

    private suspend fun handleNotificationPosted(sbn: StatusBarNotification) {
        gateStateHolder.refreshNow()
        val evaluation = gateStateHolder.latestEvaluation
        val settings = gateStateHolder.latestSettings
        val extras = sbn.notification.extras
        val (rawTitle, text) = NotificationExtrasReader.readTitleAndText(extras)
        val title = normalizeCaptureTitle(sbn.packageName, rawTitle, text)
        val category = sbn.notification.category

        val action = NotificationListenerPostHandler.evaluatePost(
            packageName = sbn.packageName,
            category = category,
            settings = settings,
            evaluation = evaluation,
            appPackageName = packageName,
        )

        if (!action.shouldIntercept) {
            if (sbn.packageName == "com.whatsapp" || sbn.packageName == "com.whatsapp.w4b") {
                errorReporter.breadcrumb(
                    "notification_listener",
                    "skip_whatsapp reason=${action.skipReason} gate=${evaluation.state} " +
                        "digestReason=${evaluation.reason}",
                )
            }
            return
        }

        val rowId = captureRepository.capture(
            CapturedNotification(
                packageName = sbn.packageName,
                title = title,
                text = text,
                postedAtEpochMs = sbn.postTime,
                category = category,
                notificationKey = sbn.key,
            ),
        )
        if (NotificationListenerPostHandler.shouldCancelAfterCapture(rowId)) {
            cancelNotification(sbn.key)
            analyticsTracker.track(
                "notification_captured",
                mapOf("package" to sbn.packageName),
            )
        } else {
            errorReporter.breadcrumb(
                "notification_listener",
                "intercept_not_persisted package=${sbn.packageName} title_len=${title.length} " +
                    "text_len=${text.length} gate=${evaluation.state} reason=${evaluation.reason}",
            )
        }
        digestNotificationManager.showDigest(captureRepository.snapshotDigestSummary())
    }

    private fun normalizeCaptureTitle(packageName: String, title: String, text: String): String {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty() && trimmed != packageName) return trimmed
        val fromText = text.lineSequence().firstOrNull()?.trim().orEmpty()
        if (fromText.isNotEmpty() && fromText.length <= 120) return fromText
        return KnownAppLabels.labelFor(packageName)?.let { "$it notification" } ?: "Notification"
    }
}
