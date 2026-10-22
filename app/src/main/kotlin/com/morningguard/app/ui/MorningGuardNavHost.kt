package com.morningguard.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.morningguard.app.BuildConfig
import com.morningguard.app.platform.PresenterMode
import com.morningguard.app.screenshot.ScreenshotIntents
import com.morningguard.app.screenshot.ScreenshotReadyNotifier
import kotlinx.coroutines.delay
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.morningguard.app.notification.DigestNotificationManager
import com.morningguard.app.ui.auth.AuthScreen
import com.morningguard.app.ui.bypass.BypassAppsScreen
import com.morningguard.app.ui.digest.DigestScreen
import com.morningguard.app.ui.emergency.EmergencyBypassScreen
import com.morningguard.app.ui.goal.GoalSuccessScreen
import com.morningguard.app.ui.goal.MorningGoalScreen
import com.morningguard.app.ui.highlights.HighlightsScreen
import com.morningguard.app.ui.home.HomeScreen
import com.morningguard.app.ui.settings.SettingsScreen
import com.morningguard.app.ui.stats.StatsScreen

@Composable
fun MorningGuardNavHost(screenshotScene: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(activity?.intent) {
        val intent = activity?.intent ?: return@LaunchedEffect
        if (intent.getBooleanExtra(DigestNotificationManager.EXTRA_OPEN_DIGEST, false)) {
            intent.removeExtra(DigestNotificationManager.EXTRA_OPEN_DIGEST)
            navController.navigate(MorningGuardRoutes.DIGEST)
        }
    }

    LaunchedEffect(screenshotScene) {
        if (!PresenterMode.enabled || screenshotScene.isNullOrBlank()) return@LaunchedEffect
        if (screenshotScene == ScreenshotIntents.SCENE_ONBOARDING_NOTIFICATION) return@LaunchedEffect
        delay(300)
        val route = when (screenshotScene) {
            ScreenshotIntents.SCENE_HOME -> MorningGuardRoutes.HOME
            ScreenshotIntents.SCENE_DIGEST -> MorningGuardRoutes.DIGEST
            ScreenshotIntents.SCENE_GOAL -> MorningGuardRoutes.GOAL
            ScreenshotIntents.SCENE_STATS -> MorningGuardRoutes.STATS
            ScreenshotIntents.SCENE_SETTINGS -> MorningGuardRoutes.SETTINGS
            ScreenshotIntents.SCENE_BYPASS -> MorningGuardRoutes.BYPASS
            ScreenshotIntents.SCENE_EMERGENCY -> MorningGuardRoutes.EMERGENCY
            ScreenshotIntents.SCENE_HIGHLIGHTS -> MorningGuardRoutes.HIGHLIGHTS
            else -> MorningGuardRoutes.HOME
        }
        if (route != MorningGuardRoutes.HOME) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
        if (BuildConfig.SCREENSHOT_MODE) {
            delay(1_200)
            ScreenshotReadyNotifier.markReady(context, screenshotScene)
        }
    }

    NavHost(navController = navController, startDestination = MorningGuardRoutes.HOME) {
        composable(MorningGuardRoutes.HOME) {
            HomeScreen(
                onOpenGoal = { navController.navigate(MorningGuardRoutes.GOAL) },
                onOpenDigest = { navController.navigate(MorningGuardRoutes.DIGEST) },
                onOpenSettings = { navController.navigate(MorningGuardRoutes.SETTINGS) },
                onOpenBypass = { navController.navigate(MorningGuardRoutes.BYPASS) },
                onOpenEmergency = { navController.navigate(MorningGuardRoutes.EMERGENCY) },
                onOpenAuth = { navController.navigate(MorningGuardRoutes.AUTH) },
                onOpenStats = { navController.navigate(MorningGuardRoutes.STATS) },
                onOpenHighlights = { navController.navigate(MorningGuardRoutes.HIGHLIGHTS) },
                onOpenBibleReader = { navController.navigate(MorningGuardRoutes.BIBLE_READER) },
            )
        }
        composable(MorningGuardRoutes.GOAL) {
            MorningGoalScreen(
                browseOnly = false,
                onBack = { navController.popBackStack() },
                onGoalCompleted = { heldCount ->
                    navController.navigate(MorningGuardRoutes.goalSuccess(heldCount)) {
                        popUpTo(MorningGuardRoutes.GOAL) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "${MorningGuardRoutes.GOAL_SUCCESS}/{heldCount}",
            arguments = listOf(navArgument("heldCount") { type = NavType.IntType }),
        ) { entry ->
            val heldCount = entry.arguments?.getInt("heldCount") ?: 0
            GoalSuccessScreen(
                heldCount = heldCount,
                onViewDigest = {
                    navController.navigate(MorningGuardRoutes.DIGEST) {
                        popUpTo(MorningGuardRoutes.HOME)
                    }
                },
                onGoHome = {
                    navController.navigate(MorningGuardRoutes.HOME) {
                        popUpTo(MorningGuardRoutes.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(MorningGuardRoutes.BIBLE_READER) {
            MorningGoalScreen(
                browseOnly = true,
                onBack = { navController.popBackStack() },
                onGoalCompleted = { },
            )
        }
        composable(MorningGuardRoutes.DIGEST) {
            DigestScreen(
                onBack = { navController.popBackStack() },
                onCompleteGoalFirst = { navController.navigate(MorningGuardRoutes.GOAL) },
            )
        }
        composable(MorningGuardRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(MorningGuardRoutes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable(MorningGuardRoutes.HIGHLIGHTS) {
            HighlightsScreen(onBack = { navController.popBackStack() })
        }
        composable(MorningGuardRoutes.BYPASS) { BypassAppsScreen(onBack = { navController.popBackStack() }) }
        composable(MorningGuardRoutes.EMERGENCY) {
            EmergencyBypassScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = {
                    navController.navigate(MorningGuardRoutes.SETTINGS) {
                        popUpTo(MorningGuardRoutes.HOME)
                    }
                },
            )
        }
        composable(MorningGuardRoutes.AUTH) { AuthScreen(onBack = { navController.popBackStack() }) }
    }
}
