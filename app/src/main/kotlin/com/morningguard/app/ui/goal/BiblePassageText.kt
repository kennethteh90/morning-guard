package com.morningguard.app.ui.goal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import com.morningguard.app.ui.components.HighlightBarActionButton
import com.morningguard.app.ui.components.HighlightDeleteButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.bible.BibleHighlightColors
import com.morningguard.domain.bible.BibleHighlightVerses
import com.morningguard.domain.bible.BiblePassageBlock
import com.morningguard.domain.bible.BiblePassageVerseParser

data class VerseSnippet(
    val number: Int,
    val text: String,
)

data class BibleVerseHighlightRequest(
    val verses: List<VerseSnippet>,
    val colorKey: String,
    val note: String,
)

/** Drives the sliding bottom bar on the morning goal screen. */
data class VerseHighlightBarState(
    val selectedVerses: Set<Int>,
    val verseSnippets: List<VerseSnippet>,
    val colorKey: String,
    val note: String,
    /** When set, the bar is in remove mode (DELETE instead of Save). */
    val existingHighlight: BibleHighlight? = null,
) {
    val isVisible: Boolean get() = selectedVerses.isNotEmpty()
    val isRemoveMode: Boolean get() = existingHighlight != null
}

@Composable
fun BiblePassageText(
    blocks: List<BiblePassageBlock>,
    modifier: Modifier = Modifier,
    savedHighlights: List<BibleHighlight> = emptyList(),
    highlightBarState: VerseHighlightBarState? = null,
    onHighlightBarStateChange: (VerseHighlightBarState?) -> Unit = {},
    defaultHighlightColorKey: String = BibleHighlightColors.DEFAULT_KEY,
    verseSelectionEnabled: Boolean = true,
) {
    val segments = remember(blocks) { BiblePassageVerseParser.segments(blocks) }
    val verseSnippets = remember(segments) {
        segments.filterIsInstance<BiblePassageVerseParser.Segment.Verse>()
            .map { VerseSnippet(it.number, it.text) }
    }
    val highlightsByVerse = remember(savedHighlights) {
        buildMap {
            for (highlight in savedHighlights) {
                for (verseNumber in BibleHighlightVerses.numbers(highlight)) {
                    put(verseNumber, highlight)
                }
            }
        }
    }

    LaunchedEffect(blocks) {
        onHighlightBarStateChange(null)
    }

    if (!verseSelectionEnabled) {
        ReadOnlyPassage(segments = segments, highlightsByVerse = highlightsByVerse, modifier = modifier)
        return
    }

    val selectedVerses = highlightBarState?.selectedVerses.orEmpty()
    val pendingColorKey = highlightBarState?.colorKey ?: defaultHighlightColorKey
    val noteDraft = highlightBarState?.note.orEmpty()

    fun updateSelection(newVerses: Set<Int>) {
        if (newVerses.isEmpty()) {
            onHighlightBarStateChange(null)
        } else {
            onHighlightBarStateChange(
                VerseHighlightBarState(
                    selectedVerses = newVerses,
                    verseSnippets = verseSnippets,
                    colorKey = pendingColorKey,
                    note = noteDraft,
                    existingHighlight = null,
                ),
            )
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is BiblePassageVerseParser.Segment.Heading -> {
                    Text(
                        segment.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                is BiblePassageVerseParser.Segment.Verse -> {
                    val saved = highlightsByVerse[segment.number]
                    val isSelected = segment.number in selectedVerses
                    val highlightBackground = if (saved != null && !isSelected) {
                        highlightColorForKey(saved.colorKey).copy(alpha = SAVED_VERSE_HIGHLIGHT_ALPHA)
                    } else {
                        null
                    }
                    VerseLine(
                        verseNumber = segment.number,
                        text = segment.text,
                        highlightBackground = highlightBackground,
                        showSelectionUnderline = isSelected,
                        onClick = {
                            if (saved != null) {
                                val verses = BibleHighlightVerses.numbers(saved)
                                onHighlightBarStateChange(
                                    VerseHighlightBarState(
                                        selectedVerses = verses,
                                        verseSnippets = verseSnippets,
                                        colorKey = saved.colorKey,
                                        note = saved.note,
                                        existingHighlight = saved,
                                    ),
                                )
                                return@VerseLine
                            }
                            val next = if (isSelected) {
                                selectedVerses - segment.number
                            } else {
                                selectedVerses + segment.number
                            }
                            updateSelection(next)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun VerseHighlightBottomBar(
    state: VerseHighlightBarState,
    onColorSelected: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDismissSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var noteDialogOpen by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = state.isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.isRemoveMode) {
                        val saved = state.existingHighlight!!
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(highlightColorForKey(saved.colorKey), CircleShape),
                        )
                        Text(
                            saved.label.ifBlank { "${state.selectedVerses.size} verses" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            "${state.selectedVerses.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val ringColor = MaterialTheme.colorScheme.primary
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            BibleHighlightColors.keys.forEach { key ->
                                val color = highlightColorForKey(key)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (key == state.colorKey) {
                                                Modifier.border(2.dp, ringColor, CircleShape)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .padding(2.dp)
                                        .background(color, CircleShape)
                                        .clickable { onColorSelected(key) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismissSelection) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear selection",
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.isRemoveMode) {
                        HighlightDeleteButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                        )
                        HighlightBarActionButton(
                            label = "Copy",
                            icon = Icons.Outlined.ContentCopy,
                            onClick = onCopy,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        HighlightBarActionButton(
                            label = "Save",
                            icon = Icons.Outlined.Save,
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                        )
                        HighlightBarActionButton(
                            label = "Note",
                            icon = Icons.Outlined.EditNote,
                            onClick = { noteDialogOpen = true },
                            modifier = Modifier.weight(1f),
                        )
                        HighlightBarActionButton(
                            label = "Copy",
                            icon = Icons.Outlined.ContentCopy,
                            onClick = onCopy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (noteDialogOpen) {
        AlertDialog(
            onDismissRequest = { noteDialogOpen = false },
            title = { Text("Note") },
            text = {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a note for selected verses") },
                    minLines = 2,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        noteDialogOpen = false
                        onSave()
                    },
                ) {
                    Text("Done")
                }
            },
        )
    }

    LaunchedEffect(state.isVisible) {
        if (!state.isVisible) noteDialogOpen = false
    }
}

@Composable
private fun ReadOnlyPassage(
    segments: List<BiblePassageVerseParser.Segment>,
    highlightsByVerse: Map<Int, BibleHighlight>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is BiblePassageVerseParser.Segment.Heading -> {
                    Text(
                        segment.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                is BiblePassageVerseParser.Segment.Verse -> {
                    val saved = highlightsByVerse[segment.number]
                    val highlightBackground = saved?.let {
                        highlightColorForKey(it.colorKey).copy(alpha = SAVED_VERSE_HIGHLIGHT_ALPHA)
                    }
                    VerseLine(
                        verseNumber = segment.number,
                        text = segment.text,
                        highlightBackground = highlightBackground,
                        showSelectionUnderline = false,
                        onClick = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerseLine(
    verseNumber: Int,
    text: String,
    highlightBackground: Color?,
    showSelectionUnderline: Boolean,
    onClick: (() -> Unit)?,
) {
    val underlineColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlightBackground != null) {
                    Modifier.background(highlightBackground, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(
                if (showSelectionUnderline) {
                    Modifier.drawBehind {
                        val y = size.height - 2.dp.toPx()
                        drawLine(
                            color = underlineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "$verseNumber",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}
