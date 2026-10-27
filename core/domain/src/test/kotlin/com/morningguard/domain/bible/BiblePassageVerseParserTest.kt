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
    fun mergesPoeticLineOneContinuationAcrossParagraphs() {
        val blocks = listOf(
            BiblePassageBlock.SectionHeading("Betrayal and Arrest in Gethsemane"),
            BiblePassageBlock.Paragraph(
                "[1] When Jesus had spoken these words, He went out with His disciples over the Brook Kidron.",
            ),
            BiblePassageBlock.Paragraph("1. And they said, \"Jesus of Nazareth.\""),
            BiblePassageBlock.Paragraph("[2] And Judas, who betrayed Him, also knew the place."),
            BiblePassageBlock.Paragraph("[3] Then Judas, having received a detachment of troops, came there."),
        )
        val verses = BiblePassageVerseParser.segments(blocks)
            .filterIsInstance<BiblePassageVerseParser.Segment.Verse>()
        assertEquals(listOf(1, 2, 3), verses.map { it.number })
        assertTrue(verses[0].text.contains("Brook Kidron"))
        assertTrue(verses[0].text.contains("Nazareth"))
    }

    @Test
    fun sortsVersesByNumberBeforeSectionHeadings() {
        val blocks = listOf(
            BiblePassageBlock.Paragraph("[3] Third. [4] Fourth."),
            BiblePassageBlock.Paragraph("[1] First."),
            BiblePassageBlock.SectionHeading("The Work of the Holy Spirit"),
            BiblePassageBlock.Paragraph("[5] Fifth. [6] Sixth."),
        )
        val verses = BiblePassageVerseParser.segments(blocks)
            .filterIsInstance<BiblePassageVerseParser.Segment.Verse>()
            .map { it.number }
        assertEquals(listOf(1, 3, 4, 5, 6), verses)
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
