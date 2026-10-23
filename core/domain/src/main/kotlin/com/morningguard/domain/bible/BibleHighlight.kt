package com.morningguard.domain.bible

data class BibleHighlight(
    val id: Long = 0,
    val remoteId: Long = 0,
    val translationId: String,
    val book: String,
    val chapter: Int,
    val label: String,
    val excerpt: String,
    val verse: Int? = null,
    /** Last verse when [verseNumbers] is unset; used with [verse] for legacy range rows. */
    val verseEnd: Int? = null,
    /** Comma-separated verse numbers, e.g. `2,3,4` — authoritative for multi-verse highlights. */
    val verseNumbers: String? = null,
    val colorKey: String = BibleHighlightColors.DEFAULT_KEY,
    val note: String = "",
    /** Legacy partial-text offsets. */
    val selectionStart: Int? = null,
    val selectionEnd: Int? = null,
    val createdAtEpochMs: Long,
)

object BibleHighlightColors {
    const val DEFAULT_KEY = "yellow"

    val keys: List<String> = listOf("yellow", "green", "blue", "pink", "purple")
}

