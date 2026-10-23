package com.morningguard.domain.bible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BibleVerseReferenceFormatTest {
    @Test
    fun compact_singleVerse() {
        assertEquals("3", BibleVerseReferenceFormat.compact(listOf(3)))
    }

    @Test
    fun compact_rangeAndSingles() {
        assertEquals("1-3,5,7-8", BibleVerseReferenceFormat.compact(listOf(1, 2, 3, 5, 7, 8)))
    }

    @Test
    fun chapterLabel() {
        assertEquals("John 3:16-18", BibleVerseReferenceFormat.chapterLabel("John", 3, listOf(16, 17, 18)))
    }
}
