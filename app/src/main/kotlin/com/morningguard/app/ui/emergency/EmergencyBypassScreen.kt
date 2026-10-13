package com.morningguard.app.ui.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

@Composable
fun EmergencyBypassScreen(onBack: () -> Unit, viewModel: EmergencyBypassViewModel = hiltViewModel()) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    val ui by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Emergency bypass", style = MaterialTheme.typography.headlineSmall)
        Text("Use a long passphrase (8+ characters) to temporarily unlock notifications.")

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
        )

        Button(onClick = { viewModel.savePassphrase(passphrase) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save passphrase")
        }
        Button(onClick = { viewModel.attemptBypass(passphrase) }, modifier = Modifier.fillMaxWidth()) {
            Text("Activate bypass now")
        }

        ui.message?.let { Text(it) }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
