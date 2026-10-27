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
        val pendingVerses = mutableListOf<Segment.Verse>()

        fun flushVerses() {
            if (pendingVerses.isEmpty()) return
            pendingVerses.sortBy { it.number }
            out.addAll(pendingVerses)
            pendingVerses.clear()
        }

        var lastVerseInSection = 0
        for (block in blocks) {
            when (block) {
                is BiblePassageBlock.SectionHeading -> {
                    flushVerses()
                    lastVerseInSection = 0
                    out.add(Segment.Heading(block.text.trim()))
                }
                is BiblePassageBlock.Paragraph -> {
                    val parsed = versesInParagraph(block.text, lastVerseInSection)
                    for (verse in parsed) {
                        mergeVerse(pendingVerses, verse)
                    }
                    if (pendingVerses.isNotEmpty()) {
                        lastVerseInSection = pendingVerses.maxOf { it.number }
                    }
                }
            }
        }
        flushVerses()
        return out
    }

    private fun mergeVerse(pending: MutableList<Segment.Verse>, verse: Segment.Verse) {
        val index = pending.indexOfFirst { it.number == verse.number }
        if (index < 0) {
            pending.add(verse)
            return
        }
        val existing = pending[index]
        val combined = "${existing.text}\n\n${verse.text}".trim()
        pending[index] = Segment.Verse(verse.number, combined)
    }

    private fun versesInParagraph(paragraph: String, lastVerseInSection: Int): List<Segment.Verse> {
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) return emptyList()

        val allMatches = verseMarker.findAll(trimmed).toList()
        if (allMatches.isEmpty()) {
            return continuationOrFirstVerse(trimmed, lastVerseInSection)
        }

        val matches = mutableListOf<MatchResult>()
        var lastVerse = lastVerseInSection
        for (match in allMatches) {
            val number = verseNumberFromMarker(match.value)
            if (!isIncreasingVerseBoundary(number, lastVerse)) continue
            matches.add(match)
            lastVerse = number
        }

        if (matches.isEmpty()) {
            return orphanedExplicitVerse(trimmed, lastVerseInSection)
                ?: continuationOrFirstVerse(
                    stripLeadingVerseMarkers(trimmed),
                    lastVerseInSection,
                )
        }

        val verses = mutableListOf<Segment.Verse>()
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = matches.getOrNull(i + 1)?.range?.first ?: trimmed.length
            val chunk = trimmed.substring(start, end).trim()
            val number = verseNumberFromMarker(matches[i].value)
            val body = stripEmbeddedPoeticLineNumbers(
                number,
                chunk.removePrefix(matches[i].value).trim(),
            )
            if (number > 0 && body.isNotEmpty()) {
                verses.add(Segment.Verse(number, body))
            }
        }
        return verses
    }

    /**
     * Proxy paragraphs can repeat an earlier `[n]` after later verses were parsed; honor the tag
     * instead of treating the line as a continuation of the highest verse so far.
     */
    private fun orphanedExplicitVerse(text: String, lastVerseInSection: Int): List<Segment.Verse>? {
        if (lastVerseInSection <= 0) return null
        val number = explicitLeadingVerseNumber(text) ?: return null
        if (number >= lastVerseInSection) return null
        val body = stripLeadingVerseMarkers(text)
        if (body.isEmpty()) return null
        return listOf(Segment.Verse(number, body))
    }

    private fun explicitLeadingVerseNumber(text: String): Int? {
        val match = verseMarker.find(text.trim()) ?: return null
        if (match.range.first != 0) return null
        val number = verseNumberFromMarker(match.value)
        return if (number > 0) number else null
    }

    private fun continuationOrFirstVerse(text: String, lastVerseInSection: Int): List<Segment.Verse> {
        if (text.isEmpty()) return emptyList()
        if (lastVerseInSection > 0) {
            return listOf(Segment.Verse(lastVerseInSection, text))
        }
        return listOf(Segment.Verse(number = 1, text = text))
    }

    private fun stripLeadingVerseMarkers(text: String): String {
        var remainder = text.trim()
        while (remainder.isNotEmpty()) {
            val match = verseMarker.find(remainder) ?: break
            if (match.range.first != 0) break
            remainder = remainder.removeRange(match.range).trimStart()
        }
        return remainder.trim()
    }

    /**
     * Poetic / quoted lines inside a verse often repeat `1.` or `[1]`; only treat markers
     * that advance the chapter verse sequence as boundaries.
     */
    private fun isIncreasingVerseBoundary(number: Int, lastVerse: Int): Boolean {
        if (number <= 0) return false
        if (lastVerse == 0) return true
        return number > lastVerse
    }

    /** Remove stanza-style `1.` / `[1]` prefixes on continuation lines within a verse. */
    private fun stripEmbeddedPoeticLineNumbers(verseNumber: Int, text: String): String {
        if (verseNumber <= 1) return text
        return text
            .replace(Regex("""(\r?\n)1\.(\s)"""), "$1$2")
            .replace(Regex("""(\r?\n)\[1\](\s)"""), "$1$2")
    }

    private fun verseNumberFromMarker(marker: String): Int {
        Regex("""\[(\d+)\]""").find(marker)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""(\d+)\.""").find(marker)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return 0
    }
}
