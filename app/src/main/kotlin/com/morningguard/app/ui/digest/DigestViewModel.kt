package com.morningguard.app.ui.digest

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.platform.PresenterMode
import com.morningguard.app.notification.DigestNotificationManager
import com.morningguard.app.platform.AppDisplayNameResolver
import com.morningguard.data.gate.MorningGateRepository
import com.morningguard.data.notification.NotificationCaptureRepository
import com.morningguard.domain.gate.GateDigestPolicy
import com.morningguard.domain.model.CapturedNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DigestAppGroup(
    val packageName: String,
    val label: String,
    val items: List<CapturedNotification>,
)

data class DigestUiState(
    val notifications: List<CapturedNotification> = emptyList(),
    val groups: List<DigestAppGroup> = emptyList(),
    val canViewDigest: Boolean = false,
    val blockedByMorningGoal: Boolean = false,
    val appLabels: Map<String, String> = emptyMap(),
)

@HiltViewModel
class DigestViewModel @Inject constructor(
    private val notificationCaptureRepository: NotificationCaptureRepository,
    private val digestNotificationManager: DigestNotificationManager,
    private val morningGateRepository: MorningGateRepository,
    private val appDisplayNameResolver: AppDisplayNameResolver,
) : ViewModel() {

    val uiState: StateFlow<DigestUiState> = kotlinx.coroutines.flow.combine(
        notificationCaptureRepository.notifications,
        morningGateRepository.gateEvaluation,
    ) { notifications, gate ->
        val canView = if (PresenterMode.enabled && notifications.isNotEmpty()) {
            true
        } else {
            GateDigestPolicy.canViewDigest(gate)
        }
        val blockedByMorningGoal = if (PresenterMode.enabled) {
            false
        } else {
            GateDigestPolicy.blockedByMorningGoal(gate)
        }
        val labels = notifications
            .map { it.packageName }
            .distinct()
            .associateWith(appDisplayNameResolver::labelFor)
        val groups = notifications
            .groupBy { it.packageName }
            .map { (packageName, items) ->
                DigestAppGroup(
                    packageName = packageName,
                    label = labels[packageName] ?: packageName,
                    items = items.sortedByDescending { it.postedAtEpochMs },
                )
            }
            .sortedByDescending { group -> group.items.maxOf { it.postedAtEpochMs } }
        DigestUiState(
            notifications = notifications,
            groups = groups,
            canViewDigest = canView,
            blockedByMorningGoal = blockedByMorningGoal,
            appLabels = labels,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DigestUiState())

    fun openSourceApp(context: Context, notification: CapturedNotification): Boolean {
        if (!uiState.value.canViewDigest) return false
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(notification.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(notification.packageName)
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchIntent.resolveActivity(pm) == null) return false
        context.startActivity(launchIntent)
        viewModelScope.launch {
            notificationCaptureRepository.remove(notification.id)
            val summary = notificationCaptureRepository.snapshotDigestSummary()
            digestNotificationManager.showDigest(summary)
        }
        return true
    }

    fun dismissDigestLog() {
        viewModelScope.launch {
            notificationCaptureRepository.clearAll()
            digestNotificationManager.clearDigest()
        }
    }

    fun dismissAllFromApp(packageName: String) {
        viewModelScope.launch {
            notificationCaptureRepository.removeByPackage(packageName)
            val summary = notificationCaptureRepository.snapshotDigestSummary()
            if (summary.count == 0) {
                digestNotificationManager.clearDigest()
            } else {
                digestNotificationManager.showDigest(summary)
            }
        }
    }
}
