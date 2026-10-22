package com.morningguard.app.ui.bypass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.ui.components.ScreenScaffold

@Composable
fun BypassAppsScreen(onBack: () -> Unit, viewModel: BypassAppsViewModel = hiltViewModel()) {
    val bypass by viewModel.bypassPackages.collectAsStateWithLifecycle()
    val allApps by viewModel.installedApps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var systemExpanded by rememberSaveable { mutableStateOf(false) }

    val q = query.trim().lowercase()
    val userApps = remember(allApps) { allApps.filter { !it.isSystemApp } }
    val systemApps = remember(allApps) { allApps.filter { it.isSystemApp } }
    val notificationApps = remember(q, allApps) {
        val base = allApps.filter { it.seenInNotifications }
        if (q.isEmpty()) base
        else base.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }
    val filteredUser = remember(q, userApps, notificationApps) {
        val listedInNotifications = notificationApps.map { it.packageName }.toSet()
        val candidates = userApps.filter { it.packageName !in listedInNotifications }
        if (q.isEmpty()) candidates
        else candidates.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }
    val filteredSystem = remember(q, systemApps) {
        if (q.isEmpty()) systemApps
        else systemApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }
    val showSystemApps = systemExpanded || (q.isNotEmpty() && filteredSystem.isNotEmpty())

    ScreenScaffold(title = "Bypass apps", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Checked apps always deliver notifications normally — they skip the morning digest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            Text(
                "${bypass.size} bypassing · ${filteredUser.size} apps · ${filteredSystem.size} system",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (notificationApps.isNotEmpty()) {
                    item {
                        Text(
                            "Seen in your notifications",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    items(notificationApps, key = { "notify-${it.packageName}" }) { app ->
                        BypassAppRow(
                            app = app,
                            checked = app.packageName in bypass,
                            showPackageName = app.label == app.packageName,
                            onToggle = { viewModel.toggle(app.packageName, it) },
                        )
                    }
                }
                item {
                    Text(
                        "All apps",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                items(filteredUser, key = { it.packageName }) { app ->
                    BypassAppRow(
                        app = app,
                        checked = app.packageName in bypass,
                        showPackageName = false,
                        onToggle = { viewModel.toggle(app.packageName, it) },
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "System apps (${filteredSystem.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { systemExpanded = !systemExpanded }) {
                            Icon(
                                if (systemExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (systemExpanded) "Collapse" else "Expand",
                            )
                        }
                    }
                }
                if (showSystemApps) {
                    items(filteredSystem, key = { "sys-${it.packageName}" }) { app ->
                        BypassAppRow(
                            app = app,
                            checked = app.packageName in bypass,
                            showPackageName = true,
                            onToggle = { viewModel.toggle(app.packageName, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BypassAppRow(
    app: InstalledApp,
    checked: Boolean,
    showPackageName: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                if (showPackageName) {
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Checkbox(checked = checked, onCheckedChange = onToggle)
        }
    }
}
