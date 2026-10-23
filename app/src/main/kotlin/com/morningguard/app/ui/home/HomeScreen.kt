package com.morningguard.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.R
import com.morningguard.app.demo.DemoSession
import com.morningguard.app.platform.PresenterMode
import com.morningguard.data.auth.AuthState
import com.morningguard.domain.gate.GateDigestPolicy
import com.morningguard.domain.gate.GateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGoal: () -> Unit,
    onOpenDigest: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBypass: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenHighlights: () -> Unit,
    onOpenBibleReader: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val signInMessage by viewModel.magicLinkSignInMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val signedIn = state.authState is AuthState.SignedIn

    LaunchedEffect(signInMessage) {
        val message = signInMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMagicLinkSignInMessage()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onHomeResumed()
        onPauseOrDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Morning Guard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (PresenterMode.demoApp) {
                        TextButton(onClick = { DemoSession.returnToPresenterHub?.invoke() }) {
                            Text("Demo")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onOpenAuth) {
                        Icon(
                            imageVector = if (signedIn) {
                                Icons.Filled.AccountCircle
                            } else {
                                Icons.Outlined.AccountCircle
                            },
                            contentDescription = if (signedIn) {
                                "Account — signed in"
                            } else {
                                "Account — sign in"
                            },
                            tint = if (signedIn) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GateStatusCard(
                gateState = state.gate?.state,
                reason = state.gate?.reason.orEmpty(),
                heldCount = state.digest.count,
                peaceSessionUntilEpochMs = state.peaceSessionUntilEpochMs,
            )

            if (!state.notificationAccessGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notification access needed", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Morning Guard can't hold notifications until listener access is on.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { viewModel.openNotificationAccess(context) }) {
                            Text("Fix in settings")
                        }
                    }
                }
            }

            val peaceActive = state.peaceSessionUntilEpochMs != null
            val canViewDigest = state.gate?.let(GateDigestPolicy::canViewDigest) == true
            val digestCount = state.digest.count
            val showDigestPrimary = !peaceActive && canViewDigest && digestCount > 0
            val showEmergencyBypass =
                state.gate?.state == GateState.LOCKED && state.gate?.reason == "morning_window"

            if (peaceActive) {
                val remainingMin = ((state.peaceSessionUntilEpochMs!! - System.currentTimeMillis()) / 60_000)
                    .coerceAtLeast(1)
                Text(
                    "Peace break · about $remainingMin min left. Notifications are collected into your digest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenDigest, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Text(stringResource(R.string.view_digest))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.endPeaceSession() },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                            Text("End")
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.startOrExtendPeaceSession() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text("Extend")
                        }
                    }
                }
            } else {
                when (state.gate?.state) {
                    GateState.LOCKED -> {
                        Button(onClick = onOpenGoal, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.WbSunny, contentDescription = null)
                                Text("Start morning goal")
                            }
                        }
                    }
                    GateState.UNLOCKED -> {
                        if (showDigestPrimary) {
                            Button(onClick = onOpenDigest, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                                    Text(stringResource(R.string.view_digest))
                                }
                            }
                            Text(
                                "$digestCount notification(s) in your digest.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "You're clear for now — notifications flow normally.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        if (showDigestPrimary) {
                            Button(onClick = onOpenDigest, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                                    Text(stringResource(R.string.view_digest))
                                }
                            }
                            Text(
                                "$digestCount notification(s) in your digest.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Outside your morning window. The gate activates during the hours you chose in setup.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (!canViewDigest && digestCount > 0) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RowWithIcon(
                                Icons.Outlined.Notifications,
                                "$digestCount notification(s) in your digest",
                            )
                            Text(
                                "Finish your morning goal to open the digest.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = onOpenGoal, modifier = Modifier.fillMaxWidth()) {
                                Text("Go to morning goal")
                            }
                        }
                    }
                }

                val morningGoalActive =
                    state.gate?.state == GateState.LOCKED && state.gate?.reason == "morning_window"
                if (state.morningGoalCompleteToday && !morningGoalActive) {
                    OutlinedButton(
                        onClick = { viewModel.startOrExtendPeaceSession() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Timer, contentDescription = null)
                            Text("Start peace break (1 hour)")
                        }
                    }
                }
            }

            OutlinedButton(onClick = onOpenBibleReader, modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.AutoStories, contentDescription = null)
                    Text("Read Bible")
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeNavRow(
                        icon = Icons.Outlined.BarChart,
                        label = "Streak & stats",
                        onClick = onOpenStats,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    HomeNavRow(
                        icon = Icons.Outlined.BookmarkBorder,
                        label = "Saved highlights",
                        onClick = onOpenHighlights,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    HomeNavRow(
                        icon = Icons.Outlined.Apps,
                        label = "Bypass apps",
                        onClick = onOpenBypass,
                    )
                }
            }

            if (showEmergencyBypass) {
                Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text("Emergency bypass", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "For genuine emergencies only — unlocks notifications for about one hour.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onOpenEmergency,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Key, contentDescription = null)
                            Text("Open emergency bypass")
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun GateStatusCard(
    gateState: GateState?,
    reason: String,
    heldCount: Int,
    peaceSessionUntilEpochMs: Long?,
) {
    val peaceActive = peaceSessionUntilEpochMs != null
    val (title, subtitle) = when {
        peaceActive ->
            "Peace break" to "Notifications go to your digest. Open it anytime below."
        gateState == GateState.LOCKED ->
            "Morning gate active" to "Complete your morning goal to release held notifications."
        gateState == GateState.UNLOCKED ->
            "Gate open" to friendlyReason(reason)
        gateState == GateState.INACTIVE ->
            "Gate resting" to "Outside your morning window."
        gateState == null -> "Loading…" to ""
        else -> "Gate resting" to ""
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today", style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Text("$heldCount held in digest", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun HomeNavRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun friendlyReason(reason: String): String = when (reason) {
    "goal_complete" -> "You finished your morning goal."
    "emergency_bypass" -> "Emergency bypass is active."
    "peace_session" -> "Peace break is active."
    else -> reason.replace('_', ' ')
}

@Composable
private fun RowWithIcon(
    icon: ImageVector,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
