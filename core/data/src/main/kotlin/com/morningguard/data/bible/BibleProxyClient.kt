package com.morningguard.data.bible

import com.morningguard.domain.bible.BiblePassageBlock
import com.morningguard.domain.bible.BiblePassageFormatter
import com.morningguard.domain.bible.BibleProxyUserMessages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class BibleProxyChapterResult(
    val reference: String,
    val blocks: List<BiblePassageBlock>,
)

@Singleton
class BibleProxyClient @Inject constructor(
    private val api: BibleProxyApi,
    private val config: BibleProxyConfig,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadChapter(translation: String, book: String, chapter: Int): Result<BibleProxyChapterResult> {
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("Bible proxy is not configured"))
        }
        return try {
            val response = api.getChapter(
                translation = translation,
                book = book,
                chapter = chapter,
            )
            val blocks = mapBlocks(response)
            if (blocks.isEmpty()) {
                val fallbackRef = response.reference.ifBlank { "$book $chapter" }
                val parsed = BiblePassageFormatter.parseChapterBody(
                    response.body,
                    fallbackRef,
                )
                if (parsed.blocks.isEmpty()) {
                    return Result.failure(IllegalStateException("Empty chapter from Bible proxy"))
                }
                return Result.success(
                    BibleProxyChapterResult(
                        reference = parsed.reference,
                        blocks = parsed.blocks,
                    ),
                )
            }
            val reference = response.reference.ifBlank { "$book $chapter" }
            Result.success(BibleProxyChapterResult(reference = reference, blocks = blocks))
        } catch (e: retrofit2.HttpException) {
            Result.failure(mapHttpException(e))
        } catch (e: java.io.IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            // Defense in depth: licensed auth must throw IOException, but never crash the UI.
            Result.failure(e)
        }
    }

    private fun mapBlocks(response: BibleProxyChapterResponse): List<BiblePassageBlock> {
        val dtos = response.blocks ?: return emptyList()
        return dtos.mapNotNull { dto ->
            when (dto.type.lowercase()) {
                "heading" -> BiblePassageBlock.SectionHeading(dto.text.trim())
                "paragraph" -> BiblePassageBlock.Paragraph(dto.text.trim())
                else -> null
            }
        }.filter { block ->
            when (block) {
                is BiblePassageBlock.SectionHeading -> block.text.isNotEmpty()
                is BiblePassageBlock.Paragraph -> block.text.isNotEmpty()
            }
        }
    }

    private fun mapHttpException(e: retrofit2.HttpException): Exception {
        val serverMessage = e.response()?.errorBody()?.use { raw ->
            val text = raw.string()
            runCatching {
                json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        if (e.code() == 401) {
            return BibleProxyLicensedAuthException(
                BibleProxyLicensedAuthException.USER_FACING_MESSAGE,
            )
        }
        return IllegalStateException(BibleProxyUserMessages.friendlyMessage(e.code(), serverMessage))
    }
}
