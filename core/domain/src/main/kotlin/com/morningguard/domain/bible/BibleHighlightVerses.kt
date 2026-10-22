package com.morningguard.domain.bible

object BibleHighlightVerses {
    /**
     * Verse numbers for this highlight.
     * Uses [BibleHighlight.verseNumbers] when set. Otherwise a single [BibleHighlight.verse] only
     * (does not expand [BibleHighlight.verseEnd], so legacy rows cannot paint whole chapters).
     */
    fun numbers(highlight: BibleHighlight): Set<Int> {
        highlight.verseNumbers?.takeIf { it.isNotBlank() }?.let { csv ->
            return csv.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        }
        val start = highlight.verse ?: return emptySet()
        return setOf(start)
    }
}
