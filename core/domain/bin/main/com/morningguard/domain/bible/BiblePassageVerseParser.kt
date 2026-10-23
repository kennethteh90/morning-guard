package com.morningguard.domain.bible

/**
 * Splits chapter [BiblePassageBlock]s into tappable verses ([1] or `1.` markers).
 */
object BiblePassageVerseParser {
    private val verseMarker = Regex("""(\[\d+\]|\d+\.)""")

    sealed interface Segment {
        data class Heading(val text: String) : Segment

        data class Verse(val number: Int, val text: String) : Segment
    }

    fun segments(blocks: List<BiblePassageBlock>): List<Segment> {
        val out = mutableListOf<Segment>()
        for (block in blocks) {
            when (block) {
                is BiblePassageBlock.SectionHeading ->
                    out.add(Segment.Heading(block.text.trim()))
                is BiblePassageBlock.Paragraph ->
                    out.addAll(versesInParagraph(block.text))
            }
        }
        return out
    }

    private fun versesInParagraph(paragraph: String): List<Segment.Verse> {
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) return emptyList()

        val matches = verseMarker.findAll(trimmed).toList()
        if (matches.isEmpty()) {
            return listOf(Segment.Verse(number = 1, text = trimmed))
        }

        val verses = mutableListOf<Segment.Verse>()
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = matches.getOrNull(i + 1)?.range?.first ?: trimmed.length
            val chunk = trimmed.substring(start, end).trim()
            val number = verseNumberFromMarker(matches[i].value)
            val body = chunk.removePrefix(matches[i].value).trim()
            if (number > 0 && body.isNotEmpty()) {
                verses.add(Segment.Verse(number, body))
            }
        }
        return verses
    }

    private fun verseNumberFromMarker(marker: String): Int {
        Regex("""\[(\d+)\]""").find(marker)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""(\d+)\.""").find(marker)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return 0
    }
}
