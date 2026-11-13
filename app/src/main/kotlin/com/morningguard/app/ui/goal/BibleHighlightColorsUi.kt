package com.morningguard.app.ui.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.morningguard.app.R
import com.morningguard.domain.bible.BibleHighlightColors

/** Background tint for saved highlights on the passage (keep subtle). */
const val SAVED_VERSE_HIGHLIGHT_ALPHA = 0.16f

fun highlightColorForKey(key: String): Color = when (key) {
    "green" -> Color(0xFFC8E6C9)
    "blue" -> Color(0xFFBBDEFB)
    "pink" -> Color(0xFFF8BBD0)
    "purple" -> Color(0xFFE1BEE7)
    else -> Color(0xFFFFF59D)
}

fun highlightColorKeyOrDefault(key: String): String =
    key.takeIf { it in BibleHighlightColors.keys } ?: BibleHighlightColors.DEFAULT_KEY

@Composable
fun highlightColorA11yLabel(colorKey: String): String {
    val normalized = highlightColorKeyOrDefault(colorKey)
    return when (normalized) {
        "yellow" -> stringResource(R.string.a11y_highlight_color_yellow)
        "green" -> stringResource(R.string.a11y_highlight_color_green)
        "blue" -> stringResource(R.string.a11y_highlight_color_blue)
        "pink" -> stringResource(R.string.a11y_highlight_color_pink)
        "purple" -> stringResource(R.string.a11y_highlight_color_purple)
        else -> stringResource(R.string.a11y_highlight_color_yellow)
    }
}

@Composable
fun HighlightColorPickerChip(
    colorKey: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatch = highlightColorForKey(colorKey)
    val ringColor = MaterialTheme.colorScheme.primary
    val label = highlightColorA11yLabel(colorKey)
    val description = if (selected) {
        stringResource(R.string.a11y_highlight_color_selected, label)
    } else {
        label
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, ringColor, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(swatch, CircleShape),
        )
    }
}
