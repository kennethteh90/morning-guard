package com.morningguard.domain.bible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BibleVerseCopyFormatTest {
    @Test
    fun singleVerse_noVerseNumberInBody() {
        val out = BibleVerseCopyFormat.format(
            book = "John",
            chapter = 3,
            translationId = "niv",
            verses = listOf(BibleVerseLine(16, "For God so loved the world.")),
        )
        assertEquals(
            """
            For God so loved the world.

            John 3:16 (NIV)
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun multipleVerses_numberedSentencesThenReference() {
        val out = BibleVerseCopyFormat.format(
            book = "Genesis",
            chapter = 1,
            translationId = "niv",
            verses = listOf(
                BibleVerseLine(2, "Now the earth was formless and empty."),
                BibleVerseLine(3, "And God said, “Let there be light.”"),
                BibleVerseLine(4, "God saw that the light was good."),
            ),
        )
        assertEquals(
            """
            2 Now the earth was formless and empty. 3 And God said, “Let there be light.” 4 God saw that the light was good.

            Genesis 1:2-4 (NIV)
            """.trimIndent(),
            out,
        )
    }
}
