package com.morningguard.app.di

import android.content.Context
import com.morningguard.app.BuildConfig
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.auth.AuthRepository
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitializer @Inject constructor(
    private val analyticsTracker: AnalyticsTracker,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize(context: Context) {
        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryAndroid.init(context) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.isDebug = BuildConfig.DEBUG
            }
        }
        analyticsTracker.track("app_open")
        scope.launch { authRepository.restoreSession() }
    }
}
