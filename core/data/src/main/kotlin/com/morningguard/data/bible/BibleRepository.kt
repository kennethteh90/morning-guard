package com.morningguard.data.bible

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BibleRepository @Inject constructor(
    private val api: BibleApi,
) {
    suspend fun loadChapter(translation: String, book: String, chapter: Int): Result<String> {
        return try {
            val bookAndChapter = "${book.lowercase().replace(' ', '+')}+$chapter"
            val response = api.getChapter(bookAndChapter, translation)
            val body = response.plainText?.takeIf { it.isNotBlank() }
                ?: response.verses.joinToString("\n") { "${it.verse}. ${it.text.trim()}" }
            Result.success(body.ifBlank { "${response.reference} (content unavailable offline)" })
        } catch (e: retrofit2.HttpException) {
            Result.failure(e)
        } catch (e: java.io.IOException) {
            Result.failure(e)
        }
    }
}
