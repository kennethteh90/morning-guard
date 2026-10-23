package com.morningguard.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.ui.onboarding.OnboardingFlowScreen

@Composable
fun MorningGuardApp(screenshotScene: String? = null) {
    val routeViewModel: AppRouteViewModel = hiltViewModel()
    val route by routeViewModel.route.collectAsStateWithLifecycle()

    when (route) {
        AppRoute.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AppRoute.Onboarding -> {
            OnboardingFlowScreen()
        }
        AppRoute.Main -> {
            MorningGuardNavHost(screenshotScene = screenshotScene)
        }
    }
}
