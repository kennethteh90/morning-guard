package com.morningguard.domain.bible

data class BibleChapterContent(
    val reference: String,
    val blocks: List<BiblePassageBlock>,
)

sealed interface BiblePassageBlock {
    data class SectionHeading(val text: String) : BiblePassageBlock

    data class Paragraph(val text: String) : BiblePassageBlock
}

object BiblePassageFormatter {
    private val verseStart = Regex("""^(\[\d+\]|\d+\.)""")

    /**
     * Parses chapter text from API.Bible (text), the proxy, or bible-api.com.
     * Section titles appear as standalone lines (e.g. "Rahab and the Spies"); verses start with [n] or n.
     */
    fun parseChapterBody(body: String, fallbackReference: String): BibleChapterContent {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            return BibleChapterContent(fallbackReference, emptyList())
        }
        val lines = trimmed.lines()
        val firstLine = lines.first().trim()
        val reference = firstLine.ifBlank { fallbackReference }
        val contentLines = if (lines.size > 1 && !isVerseLine(firstLine)) {
            lines.drop(1)
        } else {
            lines
        }
        return BibleChapterContent(reference, parseContentLines(contentLines))
    }

    fun parseContentLines(lines: List<String>): List<BiblePassageBlock> {
        val blocks = mutableListOf<BiblePassageBlock>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            if (text.isNotEmpty()) {
                blocks.add(BiblePassageBlock.Paragraph(text))
            }
            paragraph.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }
            if (isVerseLine(trimmed)) {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
            } else {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
            }
        }
        flushParagraph()
        return blocks
    }

    private fun isVerseLine(line: String): Boolean = verseStart.containsMatchIn(line)
}
