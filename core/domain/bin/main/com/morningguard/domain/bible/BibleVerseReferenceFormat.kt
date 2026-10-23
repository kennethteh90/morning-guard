package com.morningguard.domain.bible

object BibleVerseReferenceFormat {
    /** e.g. `1`, `1-3`, `1-3,5,7-8` */
    fun compact(sortedVerses: List<Int>): String {
        if (sortedVerses.isEmpty()) return ""
        if (sortedVerses.size == 1) return sortedVerses.first().toString()

        val ranges = mutableListOf<Pair<Int, Int>>()
        var rangeStart = sortedVerses.first()
        var rangeEnd = rangeStart

        for (i in 1 until sortedVerses.size) {
            val v = sortedVerses[i]
            if (v == rangeEnd + 1) {
                rangeEnd = v
            } else {
                ranges.add(rangeStart to rangeEnd)
                rangeStart = v
                rangeEnd = v
            }
        }
        ranges.add(rangeStart to rangeEnd)

        return ranges.joinToString(",") { (start, end) ->
            if (start == end) start.toString() else "$start-$end"
        }
    }

    fun chapterLabel(
        book: String,
        chapter: Int,
        verses: Collection<Int>,
        translationLabel: String? = null,
    ): String {
        val sorted = verses.sorted()
        val base = if (sorted.isEmpty()) {
            "$book $chapter"
        } else {
            "$book $chapter:${compact(sorted)}"
        }
        val version = translationLabel?.trim().orEmpty()
        return if (version.isEmpty()) base else "$base ($version)"
    }
}
