package com.morningguard.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.domain.model.AlarmSettings
import com.morningguard.domain.model.GoalType

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Morning window: ${settings.morningWindow.startHour}:${settings.morningWindow.startMinute} – " +
            "${settings.morningWindow.endHour}:${settings.morningWindow.endMinute}")

        Text("Alarm enabled")
        Switch(
            checked = settings.alarm.enabled,
            onCheckedChange = {
                viewModel.updateAlarm(settings.alarm.copy(enabled = it))
            },
        )

        if (settings.alarm.enabled) {
            Button(
                onClick = {
                    viewModel.updateAlarm(settings.alarm.copy(hour = 6, minute = 30))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Set alarm to 6:30")
            }
        }

        Text("Digest only during morning gate")
        Switch(
            checked = settings.digestOnlyDuringMorningGate,
            onCheckedChange = viewModel::toggleDigestOnlyDuringGate,
        )

        Text("Goal type")
        GoalType.entries.forEach { type ->
            Button(
                onClick = { viewModel.setGoalType(type) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(type.name)
            }
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
