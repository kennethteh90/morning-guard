package com.morningguard.app.ui.goal

import androidx.compose.ui.graphics.Color
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
