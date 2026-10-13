package com.morningguard.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.domain.gate.GateState

@Composable
fun HomeScreen(
    onOpenGoal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBypass: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenOnboarding: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Morning Guard", style = MaterialTheme.typography.headlineMedium)
        Text("Gate: ${state.gate?.state ?: "…"} (${state.gate?.reason.orEmpty()})")
        Text("Held notifications: ${state.digest.count}")
        Text("Auth: ${state.authState}")

        if (!state.notificationAccessGranted) {
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    viewModel.refreshAccess()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant notification access")
            }
        }

        if (state.gate?.state == GateState.LOCKED) {
            Button(onClick = onOpenGoal, modifier = Modifier.fillMaxWidth()) {
                Text("Complete morning goal")
            }
        }

        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
        Button(onClick = onOpenBypass, modifier = Modifier.fillMaxWidth()) { Text("Bypass apps") }
        Button(onClick = onOpenEmergency, modifier = Modifier.fillMaxWidth()) { Text("Emergency bypass") }
        Button(onClick = onOpenAuth, modifier = Modifier.fillMaxWidth()) { Text("Account") }
        Button(onClick = onOpenOnboarding, modifier = Modifier.fillMaxWidth()) { Text("How it works") }

        if (state.digest.count > 0) {
            Button(onClick = { viewModel.clearDigest() }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear held digest")
            }
        }
    }
}
