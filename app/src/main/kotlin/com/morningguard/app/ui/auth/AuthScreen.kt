package com.morningguard.app.ui.auth

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.data.auth.AuthState

@Composable
fun AuthScreen(onBack: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    var email by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val local by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Account", style = MaterialTheme.typography.headlineSmall)
        Text("Status: $authState")

        when (authState) {
            AuthState.Unconfigured -> {
                Text("Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties to enable sign-in.")
            }
            is AuthState.SignedIn -> {
                Text("Signed in as ${(authState as AuthState.SignedIn).user.email ?: "user"}")
                Button(onClick = { viewModel.signOut() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            }
            else -> {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email for magic link") },
                )
                Button(
                    onClick = { viewModel.sendMagicLink(email) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Send magic link")
                }
                Button(
                    onClick = { viewModel.signInWithGoogle(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign in with Google")
                }
            }
        }

        local.statusMessage?.let { Text(it) }
        local.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
