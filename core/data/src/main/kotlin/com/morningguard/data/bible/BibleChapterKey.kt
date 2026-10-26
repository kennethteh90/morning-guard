package com.morningguard.data.bible

data class BibleChapterKey(
    val translation: String,
    val book: String,
    val chapter: Int,
) {
    fun cacheId(): String =
        "${translation.trim().lowercase()}|${book.trim()}|$chapter"
}
