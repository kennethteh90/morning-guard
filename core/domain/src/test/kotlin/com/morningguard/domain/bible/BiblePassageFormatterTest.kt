package com.morningguard.domain.bible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BiblePassageFormatterTest {

    @Test
    fun textFallback_doesNotInferSectionHeadings() {
        val body = """
            Joshua 2

            Rahab and the Spies
                 [1] Then Joshua sent two spies.
        """.trimIndent()

        val content = BiblePassageFormatter.parseChapterBody(body, "Joshua 2")
        assertEquals("Joshua 2", content.reference)
        assertEquals(1, content.blocks.size)
        assertTrue(content.blocks[0] is BiblePassageBlock.Paragraph)
    }

    @Test
    fun wrappedLines_mergeIntoParagraph() {
        val blocks = BiblePassageFormatter.parseContentLines(
            listOf(
                "[1] In the beginning God created the heavens and the earth.",
                "Now the earth was formless and empty,",
                "[2] and darkness was over the surface of the deep.",
            ),
        )
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is BiblePassageBlock.Paragraph)
    }

    @Test
    fun openBible_verseLinesOnly() {
        val body = """
            Genesis 1

            1. In the beginning God created the heavens and the earth.
            2. The earth was formless and empty.
        """.trimIndent()

        val content = BiblePassageFormatter.parseChapterBody(body, "Genesis 1")
        assertEquals(1, content.blocks.size)
        assertTrue(content.blocks[0] is BiblePassageBlock.Paragraph)
    }
}
