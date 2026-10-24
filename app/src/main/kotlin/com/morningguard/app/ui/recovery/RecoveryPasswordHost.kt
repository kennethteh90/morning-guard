package com.morningguard.app.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryPasswordHost(
    content: @Composable () -> Unit,
    viewModel: RecoveryPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }

    content()

    if (uiState.showBlockingCloudRecovery) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Enter recovery password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your account has encrypted notes from another device. " +
                            "Enter your recovery password to open them here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Recovery password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlockCloudRecovery(password.toCharArray())
                        password = ""
                    },
                ) {
                    Text("Unlock")
                }
            },
        )
    } else if (uiState.showDedicatedSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissLater() },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Protect your notes on a new phone", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Your reflection notes are encrypted with a key that stays on this phone. " +
                        "Without a recovery password, reinstalling or switching phones means " +
                        "cloud copies cannot be opened.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Recovery password (8+ characters)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm recovery password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Button(
                    onClick = {
                        viewModel.setRecoveryPassword(password.toCharArray(), confirmation.toCharArray())
                        password = ""
                        confirmation = ""
                        viewModel.markPromptShown()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.length >= 8 && confirmation.length >= 8,
                ) {
                    Text("Set recovery password")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.dismissLater()
                        viewModel.markPromptShown()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Later — I may lose notes on a new phone")
                }
            }
        }
    }
}

@Composable
fun RecoveryPasswordBanner(
    modifier: Modifier = Modifier,
    viewModel: RecoveryPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.showBanner || uiState.hasRecoveryPassword) return
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Recovery password not set", style = MaterialTheme.typography.titleSmall)
            Text(
                "Notes are encrypted on this device but cannot be recovered on a new phone until you set one.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { viewModel.onSignedIn() }) {
                Text("Set recovery password")
            }
        }
    }
}
