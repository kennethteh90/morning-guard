package com.morningguard.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.morningguard.app.auth.AuthCallbackUri
import com.morningguard.app.auth.RecentAuthUiEvents
import com.morningguard.app.auth.SupabaseAuthBackend
import com.morningguard.app.demo.DemoPresenterScreen
import com.morningguard.app.demo.DemoSession
import com.morningguard.app.screenshot.ScreenshotDemoSeeder
import com.morningguard.app.screenshot.ScreenshotIntents
import com.morningguard.app.screenshot.ScreenshotReadyNotifier
import com.morningguard.app.sync.UserDataSyncService
import com.morningguard.app.ui.MorningGuardApp
import com.morningguard.app.ui.theme.MorningGuardTheme
import androidx.core.content.ContextCompat
import com.morningguard.data.auth.AuthBackend
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authBackend: AuthBackend

    @Inject lateinit var userDataSyncService: UserDataSyncService

    @Inject lateinit var recentAuthUiEvents: RecentAuthUiEvents

    @Inject lateinit var screenshotDemoSeeder: ScreenshotDemoSeeder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private var screenshotScene by mutableStateOf<String?>(null)
    private var showDemoPresenterHub by mutableStateOf(BuildConfig.DEMO_MODE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPostNotificationsIfNeeded()
        handleAuthIntent(intent)
        if (!BuildConfig.DEMO_MODE) {
            applyScreenshotScene(intent)
        }

        DemoSession.returnToPresenterHub = {
            screenshotScene = null
            showDemoPresenterHub = true
        }

        setContent {
            MorningGuardTheme {
                Surface {
                    if (BuildConfig.DEMO_MODE && showDemoPresenterHub) {
                        DemoPresenterScreen(
                            onLaunchScene = { scene ->
                                screenshotScene = scene
                                showDemoPresenterHub = false
                            },
                        )
                    } else {
                        MorningGuardApp(screenshotScene = screenshotScene)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        DemoSession.returnToPresenterHub = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
        if (!BuildConfig.DEMO_MODE) {
            applyScreenshotScene(intent)
        }
    }

    private fun applyScreenshotScene(intent: Intent) {
        val scene = intent.getStringExtra(ScreenshotIntents.EXTRA_SCENE)
        if (scene == null) return
        ScreenshotReadyNotifier.clear(this)
        if (BuildConfig.SCREENSHOT_MODE) {
            runBlocking {
                when (scene) {
                    ScreenshotIntents.SCENE_ONBOARDING_NOTIFICATION ->
                        screenshotDemoSeeder.seedOnboardingNotificationDemo()
                    ScreenshotIntents.SCENE_HOME,
                    ScreenshotIntents.SCENE_DIGEST,
                    ScreenshotIntents.SCENE_GOAL,
                    ScreenshotIntents.SCENE_STATS,
                    -> screenshotDemoSeeder.seedForStoreCapture()
                }
            }
        }
        screenshotScene = scene
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleAuthIntent(intent: Intent?) {
        if (!AuthCallbackUri.isAuthCallback(intent?.data)) return
        val backend = authBackend as? SupabaseAuthBackend ?: return
        scope.launch(Dispatchers.IO) {
            backend.handleDeepLink(intent!!)
                .onSuccess {
                    recentAuthUiEvents.markMagicLinkCompleted()
                    userDataSyncService.syncAllIfSignedIn()
                        .onFailure { /* logged in UserDataSyncService */ }
                }
        }
    }
}
