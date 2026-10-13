package com.morningguard.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.morningguard.app.ui.auth.AuthScreen
import com.morningguard.app.ui.bypass.BypassAppsScreen
import com.morningguard.app.ui.emergency.EmergencyBypassScreen
import com.morningguard.app.ui.goal.MorningGoalScreen
import com.morningguard.app.ui.home.HomeScreen
import com.morningguard.app.ui.onboarding.OnboardingScreen
import com.morningguard.app.ui.settings.SettingsScreen

@Composable
fun MorningGuardNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MorningGuardRoutes.HOME) {
        composable(MorningGuardRoutes.HOME) {
            HomeScreen(
                onOpenGoal = { navController.navigate(MorningGuardRoutes.GOAL) },
                onOpenSettings = { navController.navigate(MorningGuardRoutes.SETTINGS) },
                onOpenBypass = { navController.navigate(MorningGuardRoutes.BYPASS) },
                onOpenEmergency = { navController.navigate(MorningGuardRoutes.EMERGENCY) },
                onOpenAuth = { navController.navigate(MorningGuardRoutes.AUTH) },
                onOpenOnboarding = { navController.navigate(MorningGuardRoutes.ONBOARDING) },
            )
        }
        composable(MorningGuardRoutes.GOAL) { MorningGoalScreen(onBack = { navController.popBackStack() }) }
        composable(MorningGuardRoutes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable(MorningGuardRoutes.BYPASS) { BypassAppsScreen(onBack = { navController.popBackStack() }) }
        composable(MorningGuardRoutes.EMERGENCY) { EmergencyBypassScreen(onBack = { navController.popBackStack() }) }
        composable(MorningGuardRoutes.AUTH) { AuthScreen(onBack = { navController.popBackStack() }) }
        composable(MorningGuardRoutes.ONBOARDING) { OnboardingScreen(onBack = { navController.popBackStack() }) }
    }
}
