package com.morningguard.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.R
import com.morningguard.app.ui.components.ScreenContentColumn
import com.morningguard.app.ui.components.ScreenScaffold
import com.morningguard.data.auth.AuthState
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(onBack: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    var email by rememberSaveable { mutableStateOf("") }
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val local by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(local.magicLinkPhase, local.pendingEmail) {
        if (local.magicLinkPhase != MagicLinkUiPhase.AwaitingInbox) return@LaunchedEffect
        while (true) {
            viewModel.refreshResendCooldown()
            delay(1_000)
        }
    }

    ScreenScaffold(title = "Account", onBack = onBack) { padding ->
        ScreenContentColumn(modifier = Modifier.padding(padding)) {
            Text(
                stringResource(R.string.auth_signed_in_disclosure),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (authState) {
                AuthState.Unconfigured -> UnconfiguredCard()
                is AuthState.SignedIn -> SignedInCard(
                    email = (authState as AuthState.SignedIn).user.email,
                    onSignOut = { viewModel.signOut() },
                    onDeleteAccount = { viewModel.deleteAccount() },
                )
                AuthState.SignedOut,
                is AuthState.Error,
                -> when (local.magicLinkPhase) {
                    MagicLinkUiPhase.AwaitingInbox -> MagicLinkInboxCard(
                        email = local.pendingEmail.orEmpty(),
                        resendCooldownSeconds = local.resendCooldownSeconds,
                        isSending = local.isSendingMagicLink,
                        onResend = { viewModel.resendMagicLink() },
                        onEditEmail = {
                            viewModel.editMagicLinkEmail()
                            email = ""
                        },
                    )
                    MagicLinkUiPhase.EnterEmail -> SignInCard(
                        email = email,
                        onEmailChange = { email = it },
                        isSending = local.isSendingMagicLink,
                        onMagicLink = {
                            val trimmed = email.trim()
                            email = trimmed
                            viewModel.sendMagicLink(trimmed)
                        },
                    )
                }
            }

            local.statusMessage?.let {
                if (local.magicLinkPhase != MagicLinkUiPhase.AwaitingInbox) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Text(it, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            local.error?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun MagicLinkInboxCard(
    email: String,
    resendCooldownSeconds: Int,
    isSending: Boolean,
    onResend: () -> Unit,
    onEditEmail: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.MarkEmailRead,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.auth_magic_link_inbox_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.auth_magic_link_inbox_body, email),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (isSending) {
                CircularProgressIndicator()
            } else {
                val canResend = resendCooldownSeconds <= 0
                Button(
                    onClick = onResend,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canResend,
                ) {
                    Text(
                        if (canResend) {
                            stringResource(R.string.auth_magic_link_resend)
                        } else {
                            stringResource(R.string.auth_magic_link_resend_wait, resendCooldownSeconds)
                        },
                        maxLines = 1,
                    )
                }
            }
            OutlinedButton(onClick = onEditEmail, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_magic_link_use_different_email))
            }
        }
    }
}

@Composable
private fun UnconfiguredCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Sign-in not configured", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties when building the app. " +
                    "Morning Guard works fully offline without an account.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SignedInCard(
    email: String?,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "This permanently deletes your Supabase account and cloud streak stats and highlights. " +
                        "Local morning settings on this phone are kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Signed in", style = MaterialTheme.typography.titleLarge)
            Text(email ?: "Account connected", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete account")
            }
        }
    }
}

@Composable
private fun SignInCard(
    email: String,
    onEmailChange: (String) -> Unit,
    isSending: Boolean,
    onMagicLink: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sign in", style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.auth_magic_link_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSending,
            )
            Button(
                onClick = onMagicLink,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending,
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("Send magic link", maxLines = 1)
            }
        }
    }
}
