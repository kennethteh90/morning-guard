package com.morningguard.domain.bible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BibleHighlightVersesTest {
    @Test
    fun usesVerseNumbersWhenPresent() {
        val h = BibleHighlight(
            translationId = "niv",
            book = "John",
            chapter = 3,
            label = "John 3:3-4",
            excerpt = "",
            verse = 3,
            verseEnd = 4,
            verseNumbers = "3,4",
            createdAtEpochMs = 0L,
        )
        assertEquals(setOf(3, 4), BibleHighlightVerses.numbers(h))
    }

    @Test
    fun legacyRowWithoutVerseNumbers_isSingleVerseOnly() {
        val h = BibleHighlight(
            translationId = "niv",
            book = "John",
            chapter = 3,
            label = "John 3:1",
            excerpt = "",
            verse = 1,
            verseEnd = 4,
            verseNumbers = null,
            createdAtEpochMs = 0L,
        )
        assertEquals(setOf(1), BibleHighlightVerses.numbers(h))
    }
}
