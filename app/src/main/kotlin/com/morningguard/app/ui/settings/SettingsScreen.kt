package com.morningguard.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.morningguard.app.alarm.AlarmPermissionKind
import com.morningguard.app.alarm.AlarmPermissions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.demo.DemoSession
import com.morningguard.app.platform.AppDebugFlags
import com.morningguard.app.platform.PresenterMode
import com.morningguard.app.ui.backup.LocalBackupPasswordDialog
import com.morningguard.app.ui.recovery.RecoveryPasswordBanner
import com.morningguard.app.ui.components.SelectableSettingsCard
import com.morningguard.domain.bible.BibleTranslations
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.morningguard.data.auth.AuthState
import com.morningguard.domain.model.DataStorageMode
import com.morningguard.domain.model.GoalType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val accountMessage by viewModel.accountMessage.collectAsStateWithLifecycle()
    val hasEmergencyPassphrase by viewModel.hasEmergencyPassphrase.collectAsStateWithLifecycle()
    val emergencyPassphraseMessage by viewModel.emergencyPassphraseMessage.collectAsStateWithLifecycle()
    val noteCrypto by viewModel.noteCryptoState.collectAsStateWithLifecycle()
    val notesUnlocked by viewModel.notesUnlocked.collectAsStateWithLifecycle()
    val notesEncryptionMessage by viewModel.notesEncryptionMessage.collectAsStateWithLifecycle()
    var emergencyPassphraseDraft by remember { mutableStateOf("") }
    var notesPasswordDraft by remember { mutableStateOf("") }
    var notesNewPasswordDraft by remember { mutableStateOf("") }
    val stepSensorAvailable by viewModel.stepSensorAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backupScope = rememberCoroutineScope()
    var backupDialogMode by remember { mutableStateOf<BackupDialogMode?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Replace local data?") },
            text = {
                Text(
                    "Restore replaces settings, streaks, highlights, and bypass apps on this device " +
                        "with the backup file. Signed-in cloud sync is turned off. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        backupDialogMode = BackupDialogMode.Import
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
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
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri == null || password == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                viewModel.backupFileOpenFailed()
                return@launch
            }
            stream.use { viewModel.importLocalBackup(password, it) }
        }
    }

    backupDialogMode?.let { mode ->
        LocalBackupPasswordDialog(
            title = when (mode) {
                BackupDialogMode.Export -> "Encrypt backup"
                BackupDialogMode.Import -> "Decrypt backup"
            },
            confirmLabel = when (mode) {
                BackupDialogMode.Export -> "Save file"
                BackupDialogMode.Import -> "Restore"
            },
            onConfirm = { password ->
                pendingBackupPassword = password
                when (mode) {
                    BackupDialogMode.Export -> exportLauncher.launch("morning-guard-backup.mgbackup")
                    BackupDialogMode.Import -> importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                }
                backupDialogMode = null
            },
            onDismiss = { backupDialogMode = null },
        )
    }
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshStepSensorAvailability()
        if (viewModel.stepSensorAvailable.value) {
            viewModel.setGoalType(GoalType.STEP_COUNT)
        }
    }

    var missingAlarmPermissions by remember {
        mutableStateOf(AlarmPermissions.missing(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missingAlarmPermissions = AlarmPermissions.missing(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun ensureAlarmPermissions(onGranted: () -> Unit) {
        val missing = AlarmPermissions.missing(context)
        missingAlarmPermissions = missing
        if (missing.isEmpty()) {
            onGranted()
        } else {
            AlarmPermissions.openSettings(context, missing.first())
        }
    }

    fun onSelectGoalType(type: GoalType) {
        if (type == GoalType.STEP_COUNT && viewModel.needsActivityRecognitionForSteps()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            return
        }
        viewModel.setGoalType(type)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (PresenterMode.demoApp) {
                        TextButton(onClick = { DemoSession.returnToPresenterHub?.invoke() }) {
                            Text("Demo")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Morning window", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Notifications are held while the gate is locked (inside this window until your goal is done).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MorningWindowTimePickerRow(
                        label = "Start",
                        hour = settings.morningWindow.startHour,
                        minute = settings.morningWindow.startMinute,
                        onTimeSelected = viewModel::setWindowStart,
                    )
                    MorningWindowTimePickerRow(
                        label = "End",
                        hour = settings.morningWindow.endHour,
                        minute = settings.morningWindow.endMinute,
                        onTimeSelected = viewModel::setWindowEnd,
                    )
                }
            }

            if (AppDebugFlags.showDebugPanels) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Debug: test window", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Quick presets for notification listener testing. Debug builds only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { viewModel.applyDebugWindowPreset(DebugWindowPreset.IncludingNow) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Active now (30 min ago → 3 hr ahead)")
                        }
                        OutlinedButton(
                            onClick = { viewModel.applyDebugWindowPreset(DebugWindowPreset.FullDay) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Full day (0:00 – 23:59)")
                        }
                        OutlinedButton(
                            onClick = { viewModel.applyDebugWindowPreset(DebugWindowPreset.Default) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Reset to default (1:00 – 10:00)")
                        }
                        OutlinedButton(
                            onClick = { viewModel.debugResetTodayGoal() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Reset today's goal")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ensureAlarmPermissions { } },
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Alarm (Experimental)", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Plays a wake-up alarm at the time you choose (not a silent notification).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    missingAlarmPermissions.forEach { kind ->
                        val message = when (kind) {
                            AlarmPermissionKind.ExactAlarm -> "Allow exact alarms so the wake-up time is reliable."
                            AlarmPermissionKind.FullScreenIntent ->
                                "Allow full-screen alarms so the wake-up screen can appear over the lock screen."
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            OutlinedButton(onClick = { AlarmPermissions.openSettings(context, kind) }) {
                                Text("Open settings")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = settings.alarm.enabled,
                            onCheckedChange = { enabled ->
                                ensureAlarmPermissions {
                                    viewModel.updateAlarm(settings.alarm.copy(enabled = enabled))
                                }
                            },
                        )
                    }
                    MorningWindowTimePickerRow(
                        label = "Time",
                        hour = settings.alarm.hour,
                        minute = settings.alarm.minute,
                        onTimeSelected = { hour, minute ->
                            ensureAlarmPermissions {
                                viewModel.updateAlarm(settings.alarm.copy(hour = hour, minute = minute))
                            }
                        },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Morning goal type", style = MaterialTheme.typography.titleLarge)
                    goalTypeOption(
                        type = GoalType.BIBLE_CHAPTER,
                        title = "Bible chapter",
                        subtitle = "Read on screen until the timer completes.",
                        icon = Icons.Outlined.AutoStories,
                        selected = settings.morningGoal.type == GoalType.BIBLE_CHAPTER,
                        onSelect = viewModel::setGoalType,
                    )
                    goalTypeOption(
                        type = GoalType.TIMED_FOCUS,
                        title = "Timed focus",
                        subtitle = "Keep the app open for a set duration.",
                        icon = Icons.Outlined.Schedule,
                        selected = settings.morningGoal.type == GoalType.TIMED_FOCUS,
                        onSelect = viewModel::setGoalType,
                    )
                    goalTypeOption(
                        type = GoalType.STEP_COUNT,
                        title = "Step count (Experimental)",
                        subtitle = when {
                            stepSensorAvailable -> "Walk your target steps after starting."
                            viewModel.needsActivityRecognitionForSteps() ->
                                "Tap to allow activity recognition (required for steps on Android 10+)."
                            else -> "Not available on this device (no step counter sensor)."
                        },
                        icon = Icons.Outlined.DirectionsWalk,
                        selected = settings.morningGoal.type == GoalType.STEP_COUNT,
                        enabled = stepSensorAvailable || viewModel.needsActivityRecognitionForSteps(),
                        onSelect = ::onSelectGoalType,
                    )
                }
            }

            if (settings.morningGoal.type == GoalType.BIBLE_CHAPTER) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Bible translation", style = MaterialTheme.typography.titleLarge)
                        Text("Limited use", style = MaterialTheme.typography.titleSmall)
                        BibleTranslations.apiBibleOptions().forEach { option ->
                            translationOption(
                                option = option,
                                selected = settings.morningGoal.bibleTranslation.equals(option.id, ignoreCase = true),
                                onSelect = viewModel::setBibleTranslation,
                            )
                        }
                        Text("Open access", style = MaterialTheme.typography.titleSmall)
                        BibleTranslations.openBibleOptions().forEach { option ->
                            translationOption(
                                option = option,
                                selected = settings.morningGoal.bibleTranslation.equals(option.id, ignoreCase = true),
                                onSelect = viewModel::setBibleTranslation,
                            )
                        }
                    }
                }
            }

            RecoveryPasswordBanner(modifier = Modifier.padding(bottom = 8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Highlight notes", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Reflection notes are always encrypted on this device (Android Keystore). " +
                            "Cloud sync stores ciphertext only. Verse excerpts and highlight metadata are not encrypted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (noteCrypto.hasRecoveryPassword()) {
                            "Recovery password: set — you can open synced notes on a new phone."
                        } else {
                            "Recovery password: not set — notes cannot be recovered on a new device until you set one."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (noteCrypto.hasRecoveryPassword()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    when {
                        !noteCrypto.hasRecoveryPassword() -> {
                            OutlinedTextField(
                                value = notesPasswordDraft,
                                onValueChange = { notesPasswordDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Recovery password (8+ characters)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            OutlinedButton(
                                onClick = {
                                    viewModel.setupNotesEncryption(notesPasswordDraft)
                                    notesPasswordDraft = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = notesPasswordDraft.length >= 8,
                            ) {
                                Text("Set recovery password")
                            }
                        }
                        !notesUnlocked -> {
                            Text(
                                "Enter your recovery password to read notes on this device.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedTextField(
                                value = notesPasswordDraft,
                                onValueChange = { notesPasswordDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Recovery password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            OutlinedButton(
                                onClick = {
                                    viewModel.unlockNotesEncryption(notesPasswordDraft)
                                    notesPasswordDraft = ""
                                    viewModel.refreshNotesUnlocked()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = notesPasswordDraft.length >= 8,
                            ) {
                                Text("Unlock notes")
                            }
                        }
                        else -> {
                            Text(
                                "Notes unlocked on this device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedTextField(
                                value = notesPasswordDraft,
                                onValueChange = { notesPasswordDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Current notes password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            OutlinedTextField(
                                value = notesNewPasswordDraft,
                                onValueChange = { notesNewPasswordDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("New notes password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            OutlinedButton(
                                onClick = {
                                    viewModel.changeNotesEncryptionPassword(
                                        notesPasswordDraft,
                                        notesNewPasswordDraft,
                                    )
                                    notesPasswordDraft = ""
                                    notesNewPasswordDraft = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = notesPasswordDraft.length >= 8 && notesNewPasswordDraft.length >= 8,
                            ) {
                                Text("Change notes password")
                            }
                        }
                    }
                    notesEncryptionMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Emergency bypass passphrase", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Set or change your passphrase here. The Emergency screen is only for entering it during an emergency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasEmergencyPassphrase) {
                        Text(
                            "Passphrase is set on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedTextField(
                        value = emergencyPassphraseDraft,
                        onValueChange = { emergencyPassphraseDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New passphrase (8+ characters)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.saveEmergencyPassphrase(emergencyPassphraseDraft)
                            emergencyPassphraseDraft = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = emergencyPassphraseDraft.length >= 8,
                    ) {
                        Text(if (hasEmergencyPassphrase) "Update passphrase" else "Save passphrase")
                    }
                    emergencyPassphraseMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local backup", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (settings.dataStorageMode == DataStorageMode.LOCAL_ONLY) {
                            "Data stays on this device. Export an encrypted file to restore on a new phone."
                        } else {
                            "Encrypted backup includes highlights and streaks on this device (not a cloud export)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { backupDialogMode = BackupDialogMode.Export },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Export encrypted backup")
                    }
                    OutlinedButton(
                        onClick = { showRestoreConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Restore from backup")
                    }
                    backupMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleLarge)
                    when (val state = authState) {
                        AuthState.Unconfigured -> {
                            Text(
                                "Cloud sync is not configured in this build.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is AuthState.SignedIn -> {
                            Text(
                                "Signed in as ${state.user.email ?: "your account"}. Streaks and highlights sync while cloud sync is enabled.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { viewModel.signOut() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Sign out")
                            }
                            Text(
                                "Sign out clears this device’s synced highlights and digest, turns off cloud sync, and removes your Supabase session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AuthState.SignedOut,
                        is AuthState.Error,
                        -> {
                            Text(
                                "Not signed in. Use the account icon on Home to send a magic link.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    accountMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            OutlinedButton(onClick = { viewModel.replayOnboarding() }, modifier = Modifier.fillMaxWidth()) {
                Text("Run setup guide again")
            }
        }
    }
}

private enum class BackupDialogMode {
    Export,
    Import,
}

@Composable
private fun goalTypeOption(
    type: GoalType,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    onSelect: (GoalType) -> Unit,
) {
    SelectableSettingsCard(
        title = title,
        subtitle = subtitle,
        selected = selected,
        icon = icon,
        enabled = enabled,
        onClick = { onSelect(type) },
    )
}

@Composable
private fun translationOption(
    option: com.morningguard.domain.bible.BibleTranslationOption,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    SelectableSettingsCard(
        title = option.chipLabel,
        subtitle = option.fullName,
        selected = selected,
        onClick = { onSelect(option.id) },
    )
}
