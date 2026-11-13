package com.morningguard.app.ui.digest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import android.view.WindowManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.R
import com.morningguard.app.platform.AppDebugFlags
import com.morningguard.app.ui.components.ScreenScaffold
import com.morningguard.domain.model.CapturedNotification
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DigestScreen(
    onBack: () -> Unit,
    onCompleteGoalFirst: () -> Unit,
    viewModel: DigestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        if (!AppDebugFlags.screenshotMode) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (!AppDebugFlags.screenshotMode) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    ScreenScaffold(
        title = "Morning digest",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.canViewDigest) {
            Column(
                modifier = Modifier.padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Morning goal first", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Your digest unlocks after you finish today's morning goal (or use emergency bypass).",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (state.blockedByMorningGoal) {
                    OutlinedButton(onClick = onCompleteGoalFirst, modifier = Modifier.fillMaxWidth()) {
                        Text("Go to morning goal")
                    }
                }
            }
            return@ScreenScaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.digest_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Text(
                    "Tap an item to open that app. It will be removed from this list once opened.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (state.notifications.isEmpty()) {
                item {
                    DigestEmptyState()
                }
            } else {
                state.groups.forEach { group ->
                    item(key = "header-${group.packageName}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${group.label} (${group.items.size})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            TextButton(onClick = { viewModel.dismissAllFromApp(group.packageName) }) {
                                Text("Dismiss all")
                            }
                        }
                    }
                    items(group.items, key = { it.id }) { item ->
                        DigestItemCard(
                            item = item,
                            appLabel = group.label,
                            onOpen = { viewModel.openSourceApp(context, item) },
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            viewModel.dismissDigestLog()
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.dismiss_digest_log))
                    }
                }
            }
        }
    }
}

@Composable
private fun DigestEmptyState() {
    val title = stringResource(R.string.digest_empty_title)
    val detail = stringResource(R.string.digest_empty_detail)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $detail"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.NotificationsNone,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DigestItemCard(
    item: CapturedNotification,
    appLabel: String,
    onOpen: () -> Unit,
) {
    val time = DateTimeFormatter.ofPattern("h:mm a").format(
        Instant.ofEpochMilli(item.postedAtEpochMs).atZone(ZoneId.systemDefault()),
    )
    val openLabel = stringResource(R.string.a11y_digest_open_app, appLabel)
    val bodySummary = buildString {
        append(appLabel)
        append(". ")
        append(item.title)
        if (item.text.isNotBlank()) {
            append(". ")
            append(item.text)
        }
        append(". ")
        append(time)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = bodySummary
                onClick(label = openLabel) {
                    onOpen()
                    true
                }
            },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(appLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            if (item.text.isNotBlank()) {
                Text(item.text, style = MaterialTheme.typography.bodyMedium)
            }
            Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
