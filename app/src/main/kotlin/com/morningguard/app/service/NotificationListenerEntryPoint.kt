package com.morningguard.app.service

import com.morningguard.app.gate.GateStateHolder
import com.morningguard.app.notification.DigestNotificationManager
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.notification.NotificationCaptureRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationListenerEntryPoint {
    fun captureRepository(): NotificationCaptureRepository
    fun digestNotificationManager(): DigestNotificationManager
    fun gateStateHolder(): GateStateHolder
    fun analyticsTracker(): AnalyticsTracker
}
