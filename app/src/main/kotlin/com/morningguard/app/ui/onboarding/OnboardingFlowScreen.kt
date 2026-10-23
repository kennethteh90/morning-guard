package com.morningguard.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Checkbox
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.NotificationsPaused
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WbSunny
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.morningguard.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.BuildConfig
import com.morningguard.app.platform.NotificationListenerRegistrationProbe
import com.morningguard.app.screenshot.ScreenshotIntents
import com.morningguard.app.screenshot.ScreenshotReadyNotifier
import kotlinx.coroutines.delay
import com.morningguard.app.ui.backup.LocalBackupPasswordDialog
import com.morningguard.app.ui.components.OnboardingStepScaffold
import com.morningguard.domain.model.GoalType
import dagger.hilt.android.EntryPointAccessors

@Composable
fun OnboardingFlowScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.step) {
        if (!BuildConfig.SCREENSHOT_MODE) return@LaunchedEffect
        if (state.step != OnboardingStep.NotificationAccess) return@LaunchedEffect
        delay(900)
        ScreenshotReadyNotifier.markReady(context, ScreenshotIntents.SCENE_ONBOARDING_NOTIFICATION)
    }
    var showExportBackupDialog by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    val backupScope = rememberCoroutineScope()

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri == null || password == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            val stream = context.contentResolver.openOutputStream(uri)
            if (stream == null) {
                viewModel.backupFileOpenFailed()
                return@launch
            }
            stream.use { viewModel.exportLocalBackup(password, it) }
        }
    }

    if (showExportBackupDialog) {
        LocalBackupPasswordDialog(
            title = "Encrypt backup",
            confirmLabel = "Save file",
            onConfirm = { password ->
                pendingBackupPassword = password
                exportBackupLauncher.launch("morning-guard-backup.mgbackup")
            },
            onDismiss = { showExportBackupDialog = false },
        )
    }
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshStepSensorAvailability()
        viewModel.selectGoal(GoalType.STEP_COUNT)
    }

    fun onSelectStepGoal() {
        if (viewModel.needsActivityRecognitionForSteps()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            return
        }
        viewModel.selectGoal(GoalType.STEP_COUNT)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val openListenerSettings: () -> Unit = {
        val probe = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ListenerSettingsEntryPoint::class.java,
        ).listenerProbe()
        probe.openAccessSettings(context)
        viewModel.onResume()
    }

    val bottomBar: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.stepIndex > 0 && state.step != OnboardingStep.AllSet) {
                OutlinedButton(
                    onClick = { viewModel.back() },
                    modifier = Modifier.weight(SECONDARY_BOTTOM_BAR_WEIGHT),
                ) {
                    OnboardingBottomBarLabel("Back")
                }
            }
            when (state.step) {
                OnboardingStep.EmergencyPassphrase -> {
                    OutlinedButton(
                        onClick = { viewModel.next() },
                        modifier = Modifier.weight(SECONDARY_BOTTOM_BAR_WEIGHT),
                    ) {
                        OnboardingBottomBarLabel("Skip")
                    }
                    Button(
                        onClick = { viewModel.saveEmergencyAndContinue() },
                        modifier = Modifier.weight(PRIMARY_BOTTOM_BAR_WEIGHT),
                    ) {
                        OnboardingBottomBarLabel("Continue")
                    }
                }
                OnboardingStep.AllSet -> {
                    Button(
                        onClick = { viewModel.finish() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OnboardingBottomBarLabel("Go to my morning")
                    }
                }
                OnboardingStep.YourData -> {
                    if (state.signedInForOnboarding) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.continueWithLocalData() },
                                modifier = Modifier.weight(1f),
                            ) {
                                OnboardingBottomBarLabel("This device only")
                            }
                            Button(
                                onClick = { viewModel.continueWithCloudSync() },
                                modifier = Modifier.weight(1f),
                            ) {
                                OnboardingBottomBarLabel("Sync to cloud")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.continueWithLocalData() },
                                modifier = Modifier.weight(1f),
                            ) {
                                OnboardingBottomBarLabel("This device only")
                            }
                            Button(
                                onClick = { viewModel.sendMagicLinkAndContinue() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isSendingMagicLink,
                            ) {
                                if (state.isSendingMagicLink) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    OnboardingBottomBarLabel("Send link & continue")
                                }
                            }
                        }
                    }
                }
                OnboardingStep.NotificationAccess -> {
                    OutlinedButton(
                        onClick = { viewModel.next() },
                        modifier = Modifier.weight(SECONDARY_BOTTOM_BAR_WEIGHT),
                    ) {
                        OnboardingBottomBarLabel("Later")
                    }
                    Button(
                        onClick = { viewModel.next() },
                        modifier = Modifier.weight(PRIMARY_BOTTOM_BAR_WEIGHT),
                        enabled = state.notificationAccessGranted,
                    ) {
                        OnboardingBottomBarLabel("Continue")
                    }
                }
                else -> {
                    Button(
                        onClick = { viewModel.next() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OnboardingBottomBarLabel("Continue")
                    }
                }
            }
        }
    }

    when (state.step) {
        OnboardingStep.Welcome -> OnboardingStepScaffold(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            title = "Welcome to Morning Guard",
            subtitle = "Start your day with intention. We'll hold distracting notifications until your morning goal is done.",
            bottomBar = bottomBar,
        ) {
            FeatureRow(Icons.Outlined.WbSunny, "Morning gate", "Protect your first hours.")
            FeatureRow(Icons.Outlined.NotificationsPaused, "One digest", "Other apps wait in a single summary.")
            FeatureRow(Icons.Outlined.Shield, "You're in control", "Bypass apps or use an emergency passphrase.")
        }

        OnboardingStep.YourData -> OnboardingStepScaffold(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            title = "Your data",
            subtitle = if (state.signedInForOnboarding) {
                "You’re signed in. Keep syncing to the cloud or switch to this device only (signs you out)."
            } else {
                "Sync streaks and highlights across devices with a magic link, or keep everything on this phone only."
            },
            bottomBar = bottomBar,
        ) {
            if (state.signedInForOnboarding) {
                GoalChoiceCard(
                    title = "Keep on this device",
                    description = "Turns off cloud sync and signs you out on this phone. Use encrypted local backup in Settings when you want a copy.",
                    icon = Icons.Outlined.CloudOff,
                    selected = state.choseLocalOnly,
                    onClick = { viewModel.continueWithLocalData() },
                )
                GoalChoiceCard(
                    title = "Syncing to cloud",
                    description = buildString {
                        append("Stay signed in")
                        state.signedInEmail?.let { append(" as $it") }
                        append(". Streaks and highlights sync across devices.")
                    },
                    icon = Icons.Outlined.MarkEmailRead,
                    selected = !state.choseLocalOnly,
                    onClick = { viewModel.continueWithCloudSync() },
                )
            } else {
                GoalChoiceCard(
                    title = "Keep on this device",
                    description = "No account. Use encrypted local backup in Settings when you want a copy.",
                    icon = Icons.Outlined.CloudOff,
                    selected = state.choseLocalOnly,
                    onClick = { viewModel.continueWithLocalData() },
                )
                Text(
                    "Or sync with email",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = state.syncEmail,
                    onValueChange = viewModel::setSyncEmail,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email for magic link") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.MarkEmailRead, contentDescription = null) },
                )
                Text(
                    "Delivery can take a few minutes on Supabase’s default mail. Finish setup now — open the link on this phone when it arrives.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.magicLinkError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                state.magicLinkStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        OnboardingStep.HowItWorks -> OnboardingStepScaffold(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            title = "How it works",
            subtitle = "Three steps each morning — most days this takes just a few minutes.",
            bottomBar = bottomBar,
        ) {
            NumberedStep("1", "During your morning window, non-alarm notifications are held.")
            NumberedStep("2", "Complete a small, verifiable goal in the app.")
            NumberedStep("3", "Your digest unlocks and the day can begin.")
        }

        OnboardingStep.PickGoal -> OnboardingStepScaffold(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            title = "Choose your morning goal",
            subtitle = "Pick something you can honestly complete on your phone.",
            bottomBar = bottomBar,
        ) {
            GoalChoiceCard(
                title = "Read a Bible chapter",
                description = "Chapter text loads in the app; stay on screen ~1 minute.",
                icon = Icons.Outlined.AutoStories,
                selected = state.selectedGoal == GoalType.BIBLE_CHAPTER,
                onClick = { viewModel.selectGoal(GoalType.BIBLE_CHAPTER) },
            )
            GoalChoiceCard(
                title = "Timed focus",
                description = "Keep the app open until the timer finishes (default 2 minutes).",
                icon = Icons.Outlined.Schedule,
                selected = state.selectedGoal == GoalType.TIMED_FOCUS,
                onClick = { viewModel.selectGoal(GoalType.TIMED_FOCUS) },
            )
            GoalChoiceCard(
                title = "Steps (Experimental)",
                description = when {
                    state.stepSensorAvailable -> "Walk your target step count after you start the goal."
                    viewModel.needsActivityRecognitionForSteps() ->
                        "Tap to allow activity recognition (required for steps on Android 10+)."
                    else -> "Not available on this device (no step counter sensor)."
                },
                icon = Icons.Outlined.DirectionsWalk,
                selected = state.selectedGoal == GoalType.STEP_COUNT,
                enabled = state.stepSensorAvailable || viewModel.needsActivityRecognitionForSteps(),
                onClick = { onSelectStepGoal() },
            )
        }

        OnboardingStep.MorningWindow -> OnboardingStepScaffold(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            title = "When is your morning?",
            subtitle = "We'll only hold notifications during this window (you can change it later).",
            bottomBar = bottomBar,
        ) {
            WindowPreset.entries.forEach { preset ->
                GoalChoiceCard(
                    title = preset.label,
                    description = preset.description,
                    icon = Icons.Outlined.WbSunny,
                    selected = state.morningWindow == preset.window,
                    onClick = { viewModel.selectWindowPreset(preset) },
                )
            }
        }

        OnboardingStep.NotificationAccess -> OnboardingStepScaffold(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            title = "Allow notification access",
            subtitle = stringResource(R.string.notification_access_disclosure),
            bottomBar = bottomBar,
        ) {
            StatusChip(
                label = "Listener registered",
                ok = state.listenerRegistered,
            )
            StatusChip(
                label = "Access granted",
                ok = state.notificationAccessGranted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.notificationDisclosureAccepted,
                    onCheckedChange = { viewModel.setNotificationDisclosureAccepted(it) },
                )
                Text(
                    stringResource(R.string.notification_access_disclosure_checkbox),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Button(
                onClick = openListenerSettings,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.notificationDisclosureAccepted,
            ) {
                Text(stringResource(R.string.open_notification_access_settings))
            }
            Text(
                "If you don't see the app, return here after installing the latest APK. On Android 14+, the system screen may open directly for Morning Guard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OnboardingStep.EmergencyPassphrase -> OnboardingStepScaffold(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            title = "Emergency bypass",
            subtitle = "Optional. You can set this now or later in Settings. The Emergency screen is only for using it during an emergency.",
            bottomBar = bottomBar,
        ) {
            OutlinedTextField(
                value = state.emergencyPassphrase,
                onValueChange = viewModel::setEmergencyPassphrase,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Passphrase (8+ characters)") },
                singleLine = true,
            )
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        OnboardingStep.AllSet -> OnboardingStepScaffold(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            title = "You're all set",
            subtitle = "Tomorrow morning, open the app and complete your goal to release your notification digest.",
            bottomBar = bottomBar,
        ) {
            if (state.magicLinkSent) {
                Text(
                    "When your sign-in email arrives, open the link on this phone to enable cloud sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.choseLocalOnly) {
                Text(
                    "Your data stays on this device. Save an encrypted backup anytime from Settings — or now:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { showExportBackupDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export encrypted backup")
                }
            }
            state.backupMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Tip: add bypass apps in Settings if work or family apps must always come through.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val SECONDARY_BOTTOM_BAR_WEIGHT = 0.72f
private const val PRIMARY_BOTTOM_BAR_WEIGHT = 1.28f

@Composable
private fun OnboardingBottomBarLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelLarge,
    )
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ListenerSettingsEntryPoint {
    fun listenerProbe(): NotificationListenerRegistrationProbe
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumberedStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GoalChoiceCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected && enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected && enabled) 4.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(
            text = "$label: ${if (ok) "Yes" else "Not yet"}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
