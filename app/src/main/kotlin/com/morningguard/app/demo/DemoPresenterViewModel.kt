package com.morningguard.app.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.gate.GateStateHolder
import com.morningguard.app.screenshot.ScreenshotDemoSeeder
import com.morningguard.app.screenshot.ScreenshotIntents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DemoScenario(
    val title: String,
    val description: String,
    val scene: String,
) {
    OnboardingNotification(
        title = "Onboarding — notification access",
        description = "Disclosure step before opening system settings",
        scene = ScreenshotIntents.SCENE_ONBOARDING_NOTIFICATION,
    ),
    GateLockedHome(
        title = "Home — morning gate active",
        description = "Goal in progress, sample held notifications in the background",
        scene = ScreenshotIntents.SCENE_HOME,
    ),
    MorningGoal(
        title = "Morning goal (Bible)",
        description = "Psalm 23 reading goal with gate on",
        scene = ScreenshotIntents.SCENE_GOAL,
    ),
    DigestUnlocked(
        title = "Digest after goal",
        description = "Goal marked complete; sample digest notifications",
        scene = ScreenshotIntents.SCENE_DIGEST,
    ),
    PeaceBreak(
        title = "Peace break active",
        description = "Goal done; peace session extended on Home",
        scene = ScreenshotIntents.SCENE_HOME,
    ),
    Stats(
        title = "Streaks & stats",
        description = "Sample morning history",
        scene = ScreenshotIntents.SCENE_STATS,
    ),
    Highlights(
        title = "Saved highlights",
        description = "Sample verse highlight",
        scene = ScreenshotIntents.SCENE_HIGHLIGHTS,
    ),
    BypassApps(
        title = "Bypass apps",
        description = "Picker with demo notifications in the list",
        scene = ScreenshotIntents.SCENE_BYPASS,
    ),
    EmergencyBypass(
        title = "Emergency bypass",
        description = "Gate locked — emergency passphrase screen",
        scene = ScreenshotIntents.SCENE_EMERGENCY,
    ),
    Settings(
        title = "Settings",
        description = "Alarm, backup, window, and account sections",
        scene = ScreenshotIntents.SCENE_SETTINGS,
    ),
}

@HiltViewModel
class DemoPresenterViewModel @Inject constructor(
    private val demoSeeder: ScreenshotDemoSeeder,
    private val gateStateHolder: GateStateHolder,
) : ViewModel() {

    private val _launchScene = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val launchScene: SharedFlow<String> = _launchScene.asSharedFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun runScenario(scenario: DemoScenario) {
        viewModelScope.launch {
            runCatching {
                when (scenario) {
                    DemoScenario.OnboardingNotification ->
                        demoSeeder.seedOnboardingNotificationDemo()
                    DemoScenario.GateLockedHome ->
                        demoSeeder.seedGateLockedHome()
                    DemoScenario.MorningGoal ->
                        demoSeeder.seedGateLockedHome()
                    DemoScenario.DigestUnlocked ->
                        demoSeeder.seedDigestUnlocked()
                    DemoScenario.PeaceBreak ->
                        demoSeeder.seedPeaceBreakActive()
                    DemoScenario.Stats ->
                        demoSeeder.seedForStoreCapture()
                    DemoScenario.Highlights ->
                        demoSeeder.seedSampleHighlights()
                    DemoScenario.BypassApps ->
                        demoSeeder.seedGateLockedHome()
                    DemoScenario.EmergencyBypass ->
                        demoSeeder.seedGateLockedHome()
                    DemoScenario.Settings ->
                        demoSeeder.seedForStoreCapture()
                }
            }.onFailure { err ->
                _message.emit(err.message ?: "Could not load demo data.")
                return@launch
            }
            gateStateHolder.refreshNow()
            _launchScene.emit(scenario.scene)
        }
    }

    fun resetAllDemoData() {
        viewModelScope.launch {
            runCatching { demoSeeder.resetDemoData() }
                .onSuccess { _message.emit("Demo data cleared.") }
                .onFailure { err -> _message.emit(err.message ?: "Reset failed.") }
        }
    }
}
