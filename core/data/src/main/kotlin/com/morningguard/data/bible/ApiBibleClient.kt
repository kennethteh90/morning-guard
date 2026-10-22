package com.morningguard.data.bible

import com.morningguard.domain.bible.BibleBookUsfm
import com.morningguard.domain.bible.BibleTranslations
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiBibleClient @Inject constructor(
    private val api: ApiBibleApi,
    private val config: ApiBibleConfig,
) {
    private val resolvedIds = mutableMapOf<String, String>()

    suspend fun loadChapter(translationId: String, book: String, chapter: Int): Result<String> {
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("API.Bible key is not configured"))
        }
        val option = BibleTranslations.find(translationId)
            ?: return Result.failure(IllegalArgumentException("Unknown translation"))
        if (!option.usesApiBible) {
            return Result.failure(IllegalArgumentException("Translation is not served by API.Bible"))
        }
        val abbrev = option.apiBibleAbbreviation
            ?: return Result.failure(IllegalStateException("Missing API.Bible abbreviation"))
        val chapterId = BibleBookUsfm.chapterId(book, chapter)
            ?: return Result.failure(IllegalArgumentException("Unknown book: $book"))

        return try {
            val bibleId = resolveBibleId(translationId, abbrev)
            val response = api.getChapter(bibleId = bibleId, chapterId = chapterId)
            val content = response.data?.content?.trim().orEmpty()
            if (content.isBlank()) {
                Result.failure(IllegalStateException("Empty chapter from API.Bible"))
            } else {
                val reference = response.data?.reference?.takeIf { it.isNotBlank() } ?: "$book $chapter"
                Result.success("$reference\n\n$content")
            }
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401 -> Result.failure(IllegalStateException("API.Bible rejected the API key"))
                403 -> Result.failure(
                    IllegalStateException(
                        "API.Bible denied access to $abbrev. Confirm it is licensed for your app in the dashboard.",
                    ),
                )
                404 -> Result.failure(IllegalStateException("Chapter not found ($chapterId) for $abbrev"))
                else -> Result.failure(e)
            }
        } catch (e: java.io.IOException) {
            Result.failure(e)
        }
    }

    private suspend fun resolveBibleId(translationId: String, abbreviation: String): String {
        config.bibleIdOverrides[translationId]?.takeIf { it.isNotBlank() }?.let { return it }
        resolvedIds[translationId]?.let { return it }

        val bibles = api.listBibles().data
        val match = bibles.firstOrNull { summary -> matchesAbbrev(summary, abbreviation) }
            ?: throw IllegalStateException(
                "No $abbreviation Bible on your API.Bible account. Add it in the dashboard or set API_BIBLE_${abbreviation.uppercase()}_ID in local.properties.",
            )
        resolvedIds[translationId] = match.id
        return match.id
    }

    private fun matchesAbbrev(summary: ApiBibleSummary, abbreviation: String): Boolean {
        val target = abbreviation.uppercase()
        return summary.abbreviationLocal.equals(target, ignoreCase = true) ||
            summary.abbreviation.equals(target, ignoreCase = true) ||
            summary.abbreviationLocal.contains(target, ignoreCase = true) ||
            summary.nameLocal.contains(abbreviation, ignoreCase = true) ||
            summary.name.contains(abbreviation, ignoreCase = true)
    }
}
