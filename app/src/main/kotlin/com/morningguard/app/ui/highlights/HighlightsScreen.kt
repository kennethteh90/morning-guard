package com.morningguard.app.ui.highlights

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.ui.recovery.RecoveryPasswordBanner
import com.morningguard.app.ui.components.ConfirmRemoveHighlightDialog
import com.morningguard.app.ui.components.SavedHighlightBottomBar
import com.morningguard.app.ui.components.ScreenScaffold
import com.morningguard.app.ui.goal.highlightColorForKey
import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.bible.BibleHighlightColors
import com.morningguard.domain.bible.BibleVerseCopyFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HighlightsScreen(
    onBack: () -> Unit,
    viewModel: HighlightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedHighlight by remember { mutableStateOf<BibleHighlight?>(null) }
    var pendingRemove by remember { mutableStateOf<BibleHighlight?>(null) }

    pendingRemove?.let { highlight ->
        ConfirmRemoveHighlightDialog(
            highlight = highlight,
            onConfirm = {
                viewModel.deleteHighlight(highlight)
                pendingRemove = null
                selectedHighlight = null
            },
            onDismiss = { pendingRemove = null },
        )
    }

    ScreenScaffold(
        title = "Saved highlights",
        onBack = onBack,
        bottomBar = {
            selectedHighlight?.let { highlight ->
                SavedHighlightBottomBar(
                    highlight = highlight,
                    onDelete = { pendingRemove = highlight },
                    onShare = {
                        val text = BibleVerseCopyFormat.shareText(highlight)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share highlight"))
                    },
                    onDismiss = { selectedHighlight = null },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecoveryPasswordBanner()
                    Text(
                        "Tap a highlight for Delete or Share. Cloud sync is optional (Account).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search highlights") },
                    )
                    Text(
                        "Color",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.colorFilter == null,
                            onClick = { viewModel.setColorFilter(null) },
                            label = { Text("All") },
                        )
                        BibleHighlightColors.keys.forEach { key ->
                            FilterChip(
                                selected = state.colorFilter == key,
                                onClick = {
                                    viewModel.setColorFilter(if (state.colorFilter == key) null else key)
                                },
                                label = { Text(key.replaceFirstChar { it.uppercase() }) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(highlightColorForKey(key)),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            if (state.highlights.isEmpty()) {
                item {
                    Text(
                        when {
                            state.query.isNotBlank() || state.colorFilter != null ->
                                "No matches for your filters."
                            else -> "No highlights saved yet."
                        },
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                items(state.highlights, key = { it.id }) { highlight ->
                    HighlightCard(
                        highlight = highlight,
                        selected = selectedHighlight?.let {
                            it.id == highlight.id && it.remoteId == highlight.remoteId
                        } == true,
                        onClick = {
                            selectedHighlight = if (
                                selectedHighlight?.id == highlight.id &&
                                selectedHighlight?.remoteId == highlight.remoteId
                            ) {
                                null
                            } else {
                                highlight
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightCard(
    highlight: BibleHighlight,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val passageText = BibleVerseCopyFormat.bodyFromHighlight(highlight)
    val referenceLine = BibleVerseCopyFormat.referenceLine(highlight)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(highlightColorForKey(highlight.colorKey), CircleShape),
                )
                Text(
                    referenceLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (highlight.note.isNotBlank()) {
                Text(
                    "Note",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(highlight.note, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                passageText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
