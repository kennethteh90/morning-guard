package com.morningguard.domain.bible

data class BibleVerseLine(
    val number: Int,
    val text: String,
)

object BibleVerseCopyFormat {
    /**
     * Single verse: plain text, blank line, then `Book Chapter:Verse`.
     * Multiple: `2 text. 3 text. 4 text` then blank line, then reference (no trailing comma).
     */
    fun format(
        book: String,
        chapter: Int,
        translationId: String,
        verses: List<BibleVerseLine>,
    ): String {
        val sorted = verses.sortedBy { it.number }
        if (sorted.isEmpty()) return ""

        val reference = BibleVerseReferenceFormat.chapterLabel(
            book,
            chapter,
            sorted.map { it.number },
            BibleTranslations.chipLabel(translationId),
        )
        return "${formatBody(sorted)}\n\n$reference"
    }

    fun referenceLine(highlight: BibleHighlight): String {
        val verses = BibleHighlightVerses.numbers(highlight).sorted()
        return BibleVerseReferenceFormat.chapterLabel(
            highlight.book,
            highlight.chapter,
            verses,
            BibleTranslations.chipLabel(highlight.translationId),
        )
    }

    fun formatBody(verses: List<BibleVerseLine>): String {
        val sorted = verses.sortedBy { it.number }
        if (sorted.isEmpty()) return ""
        if (sorted.size == 1) return sorted.single().text.trim()
        return sorted.joinToString(" ") { line ->
            val text = line.text.trim()
            val punctuated = if (endsSentence(text)) text else "$text."
            "${line.number} $punctuated"
        }
    }

    /** Parse stored excerpt or fall back to raw text. */
    /** Plain text for clipboard or share sheet. */
    fun shareText(highlight: BibleHighlight): String {
        val lines = linesFromHighlight(highlight)
        val bodyReference = if (lines.isNotEmpty()) {
            format(
                highlight.book,
                highlight.chapter,
                highlight.translationId,
                lines,
            )
        } else {
            "${bodyFromHighlight(highlight)}\n\n${referenceLine(highlight)}"
        }
        val note = highlight.note.trim()
        return if (note.isEmpty()) bodyReference else "$bodyReference\n\n$note"
    }

    fun bodyFromHighlight(highlight: BibleHighlight): String {
        val parsed = parseNumberedLines(highlight.excerpt)
        if (parsed.isNotEmpty()) {
            return formatBody(parsed)
        }
        return highlight.excerpt.trim()
    }

    fun linesFromHighlight(highlight: BibleHighlight): List<BibleVerseLine> {
        val parsed = parseNumberedLines(highlight.excerpt)
        if (parsed.isNotEmpty()) return parsed.sortedBy { it.number }
        val numbers = BibleHighlightVerses.numbers(highlight).sorted()
        if (numbers.size == 1) {
            return listOf(BibleVerseLine(numbers.first(), highlight.excerpt.trim()))
        }
        return emptyList()
    }

    private fun parseNumberedLines(excerpt: String): List<BibleVerseLine> {
        val linePattern = Regex("""^(\d+)\.\s*(.+)$""")
        return excerpt.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val match = linePattern.matchEntire(line) ?: return@mapNotNull null
                val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                BibleVerseLine(number, match.groupValues[2].trim())
            }
            .toList()
    }

    private fun endsSentence(text: String): Boolean {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return false
        if (trimmed.last() in ".!?") return true
        if (trimmed.last() in quoteChars) {
            val beforeQuote = trimmed.dropLast(1).trimEnd().lastOrNull()
            return beforeQuote != null && beforeQuote in ".!?"
        }
        return false
    }

    private val quoteChars = setOf('"', '\u201C', '\u201D', '\u2018', '\u2019')
}
