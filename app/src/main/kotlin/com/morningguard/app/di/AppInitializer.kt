package com.morningguard.app.di

import android.content.Context
import com.morningguard.app.BuildConfig
import com.morningguard.app.alarm.MorningAlarmScheduler
import com.morningguard.app.screenshot.ScreenshotDemoSeeder
import com.morningguard.app.gate.GateStateHolder
import com.morningguard.app.monitoring.SentryEnvironment
import com.morningguard.app.sync.UserDataSyncService
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.monitoring.ErrorReporter
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import com.morningguard.data.security.NoteEncryptionRepository
import com.morningguard.data.gate.PeaceSessionRepository
import com.morningguard.data.settings.SettingsRepository
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitializer @Inject constructor(
    private val analyticsTracker: AnalyticsTracker,
    private val authRepository: AuthRepository,
    private val gateStateHolder: GateStateHolder,
    private val settingsRepository: SettingsRepository,
    private val userDataSyncService: UserDataSyncService,
    private val noteEncryptionRepository: NoteEncryptionRepository,
    private val peaceSessionRepository: PeaceSessionRepository,
    private val morningAlarmScheduler: MorningAlarmScheduler,
    private val screenshotDemoSeeder: ScreenshotDemoSeeder,
    private val errorReporter: ErrorReporter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize(context: Context) {
        if (BuildConfig.SENTRY_DSN.isNotBlank() && !SentryEnvironment.isFirebaseTestLab(context)) {
            try {
                SentryAndroid.init(context) { options ->
                    options.dsn = BuildConfig.SENTRY_DSN
                    options.isDebug = BuildConfig.DEBUG && !BuildConfig.SCREENSHOT_MODE
                    options.environment = if (BuildConfig.DEBUG && !BuildConfig.SCREENSHOT_MODE) {
                        "debug"
                    } else {
                        "production"
                    }
                    options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}"
                    options.tracesSampleRate = if (BuildConfig.DEBUG && !BuildConfig.SCREENSHOT_MODE) 0.0 else 0.1
                    options.isSendDefaultPii = false
                    options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                        if (SentryEnvironment.isFirebaseTestLab(context)) {
                            return@BeforeSendCallback null
                        }
                        event.request?.let { request ->
                            request.headers?.remove("Authorization")
                            request.headers?.remove("apikey")
                        }
                        event
                    }
                }
            } catch (_: RuntimeException) {
                // Invalid DSN or ProGuard edge case — app must still launch.
            }
        }
        if (BuildConfig.SCREENSHOT_MODE) {
            runBlocking(Dispatchers.IO) {
                screenshotDemoSeeder.seedForStoreCapture()
                gateStateHolder.refreshNow()
            }
        }
        analyticsTracker.track("app_open")
        scope.launch {
            gateStateHolder.refreshNow()
            peaceSessionRepository.reconcileStoredPeaceSession()
            morningAlarmScheduler.schedule(settingsRepository.currentSettings().alarm)
            noteEncryptionRepository.ensureDekReady().onFailure { error ->
                errorReporter.reportNonFatal(
                    error,
                    tags = mapOf("area" to "note_crypto"),
                    extras = mapOf("phase" to "ensureDekReady"),
                )
            }
            noteEncryptionRepository.tryUnlockFromKeystore()
            settingsRepository.ensureDefaultBypassPackages()
            authRepository.restoreSession()
            userDataSyncService.syncAllIfSignedIn()
            authRepository.state.collect { state ->
                if (state is AuthState.SignedIn) {
                    userDataSyncService.syncAllIfSignedIn()
                        .onFailure { /* logged in UserDataSyncService */ }
                }
            }
        }
    }
}
