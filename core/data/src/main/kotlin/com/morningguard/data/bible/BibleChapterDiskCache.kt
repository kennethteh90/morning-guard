package com.morningguard.data.bible

import android.content.Context
import com.morningguard.domain.bible.BibleChapterCachePolicy
import com.morningguard.domain.bible.BibleChapterContent
import com.morningguard.domain.bible.BiblePassageBlock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class BibleChapterCacheEntry(
    val cachedAtEpochMs: Long,
    val reference: String,
    val blocks: List<BibleProxyBlockDto>,
)

@Singleton
class BibleChapterDiskCache @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val directory = File(context.cacheDir, "bible-chapters").apply { mkdirs() }

    suspend fun get(key: BibleChapterKey): BibleChapterContent? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.isFile) return@withContext null
        val entry = runCatching {
            json.decodeFromString<BibleChapterCacheEntry>(file.readText())
        }.getOrNull() ?: run {
            file.delete()
            return@withContext null
        }
        val ageMs = System.currentTimeMillis() - entry.cachedAtEpochMs
        if (ageMs > BibleChapterCachePolicy.ttlMsForTranslation(key.translation)) {
            file.delete()
            return@withContext null
        }
        entry.toContent()
    }

    suspend fun put(key: BibleChapterKey, content: BibleChapterContent) = withContext(Dispatchers.IO) {
        val blocks = content.blocks.mapNotNull { block ->
            when (block) {
                is BiblePassageBlock.SectionHeading ->
                    BibleProxyBlockDto(type = "heading", text = block.text)
                is BiblePassageBlock.Paragraph ->
                    BibleProxyBlockDto(type = "paragraph", text = block.text)
            }
        }
        val entry = BibleChapterCacheEntry(
            cachedAtEpochMs = System.currentTimeMillis(),
            reference = content.reference,
            blocks = blocks,
        )
        fileFor(key).writeText(json.encodeToString(BibleChapterCacheEntry.serializer(), entry))
    }

    suspend fun containsFresh(key: BibleChapterKey): Boolean = get(key) != null

    private fun fileFor(key: BibleChapterKey): File {
        val safeName = key.cacheId().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(directory, "$safeName.json")
    }

    private fun BibleChapterCacheEntry.toContent(): BibleChapterContent {
        val mapped = blocks.mapNotNull { dto ->
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
        return BibleChapterContent(reference, mapped)
    }
}
