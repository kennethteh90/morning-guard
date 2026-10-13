package com.morningguard.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("How Morning Guard works", style = MaterialTheme.typography.headlineSmall)
        Text(
            "1. Grant notification access so the app can hold non-alarm notifications during your morning window.\n" +
                "2. Choose a verifiable morning goal (Bible chapter, timed focus, or steps).\n" +
                "3. Complete the goal to release your digest, or use your emergency passphrase if needed.\n" +
                "4. Optionally enable a morning alarm and select apps that should bypass collection.",
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
