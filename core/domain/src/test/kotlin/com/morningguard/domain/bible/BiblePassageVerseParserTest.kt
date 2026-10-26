package com.morningguard.domain.bible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BiblePassageVerseParserTest {
    @Test
    fun splitsBracketVersesInOneParagraph() {
        val blocks = listOf(
            BiblePassageBlock.Paragraph("[1] First. [2] Second."),
        )
        val segments = BiblePassageVerseParser.segments(blocks)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is BiblePassageVerseParser.Segment.Verse)
        val v1 = segments[0] as BiblePassageVerseParser.Segment.Verse
        assertEquals(1, v1.number)
        assertEquals("First.", v1.text)
    }

    @Test
    fun splitsDotVersesOnSeparateLines() {
        val blocks = listOf(
            BiblePassageBlock.Paragraph(
                "1. Yahweh is my shepherd:\nI shall lack nothing.\n2. He makes me lie down in green pastures.",
            ),
        )
        val segments = BiblePassageVerseParser.segments(blocks)
        assertEquals(2, segments.filterIsInstance<BiblePassageVerseParser.Segment.Verse>().size)
        val v2 = segments[1] as BiblePassageVerseParser.Segment.Verse
        assertEquals(2, v2.number)
    }

    @Test
    fun ignoresPoeticLineOneMarkersInsideLaterVerse() {
        val blocks = listOf(
            BiblePassageBlock.Paragraph(
                """
                [37] Though he had done so many signs before them, yet they didn't believe in him.
                [38] that the word of Isaiah the prophet might be fulfilled, which he spoke:
                1. "Lord, who has believed our report?
                1. And to whom has the arm of the Lord been revealed?"
                [39] Therefore they couldn't believe.
                """.trimIndent(),
            ),
        )
        val verses = BiblePassageVerseParser.segments(blocks)
            .filterIsInstance<BiblePassageVerseParser.Segment.Verse>()
        assertEquals(3, verses.size)
        assertEquals(37, verses[0].number)
        assertEquals(38, verses[1].number)
        assertTrue(verses[1].text.contains("Lord, who has believed"))
        assertTrue(verses[1].text.contains("And to whom has the arm"))
        assertTrue("1." !in verses[1].text && "[1]" !in verses[1].text)
        assertEquals(39, verses[2].number)
    }

    @Test
    fun includesSectionHeadings() {
        val blocks = listOf(
            BiblePassageBlock.SectionHeading("The Beginning"),
            BiblePassageBlock.Paragraph("[1] In the beginning"),
        )
        val segments = BiblePassageVerseParser.segments(blocks)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is BiblePassageVerseParser.Segment.Heading)
    }
}
