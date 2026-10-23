package com.morningguard.app.ui.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.ui.components.ScreenContentColumn
import com.morningguard.app.ui.components.ScreenScaffold

@Composable
fun EmergencyBypassScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: EmergencyBypassViewModel = hiltViewModel(),
) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val hasPassphrase by viewModel.hasPassphrase.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Emergency bypass", onBack = onBack) { padding ->
        ScreenContentColumn(modifier = Modifier.padding(padding)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "For genuine emergencies only. Entering your passphrase unlocks notifications for about one hour.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!hasPassphrase) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "No emergency passphrase is set on this device.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Set one in Settings before you need it — not while notifications are already held.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Settings")
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Emergency passphrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                        Button(
                            onClick = { viewModel.attemptBypass(passphrase) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Activate bypass now")
                        }
                        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Change passphrase in Settings")
                        }
                    }
                }
            }

            ui.message?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(it, modifier = Modifier.padding(16.dp))
                }
            }
            ui.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}
