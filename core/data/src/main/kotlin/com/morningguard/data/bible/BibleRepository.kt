package com.morningguard.data.bible

import com.morningguard.domain.bible.BibleChapterContent
import com.morningguard.domain.bible.BiblePassageBlock
import com.morningguard.domain.bible.BiblePassageFormatter
import com.morningguard.domain.bible.BibleTranslationSource
import com.morningguard.domain.bible.BibleTranslations
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BibleRepository @Inject constructor(
    private val openBibleApi: BibleApi,
    private val bibleProxyClient: BibleProxyClient,
    private val bibleProxyConfig: BibleProxyConfig,
    private val chapterMemoryCache: BibleChapterMemoryCache,
    private val chapterDiskCache: BibleChapterDiskCache,
) {
    suspend fun loadChapter(
        translation: String,
        book: String,
        chapter: Int,
        forceRefresh: Boolean = false,
    ): Result<BibleChapterContent> {
        val key = BibleChapterKey(
            translation = BibleTranslations.normalizeId(translation),
            book = book,
            chapter = chapter,
        )
        if (!forceRefresh) {
            chapterMemoryCache.get(key)?.let { return Result.success(it) }
            chapterDiskCache.get(key)?.let { cached ->
                chapterMemoryCache.put(key, cached)
                return Result.success(cached)
            }
        }
        return fetchChapterFromNetwork(key).onSuccess { content ->
            chapterMemoryCache.put(key, content)
            chapterDiskCache.put(key, content)
        }
    }

    /** Warm disk/memory cache for adjacent navigation without blocking the reader. */
    suspend fun prefetchChapter(translation: String, book: String, chapter: Int) {
        val key = BibleChapterKey(
            translation = BibleTranslations.normalizeId(translation),
            book = book,
            chapter = chapter,
        )
        if (chapterMemoryCache.contains(key) || chapterDiskCache.containsFresh(key)) {
            return
        }
        fetchChapterFromNetwork(key).onSuccess { content ->
            chapterMemoryCache.put(key, content)
            chapterDiskCache.put(key, content)
        }
    }

    private suspend fun fetchChapterFromNetwork(key: BibleChapterKey): Result<BibleChapterContent> {
        val translation = key.translation
        val book = key.book
        val chapter = key.chapter
        val option = BibleTranslations.find(translation)
            ?: return loadOpenBibleChapter(BibleTranslations.defaultId, book, chapter)

        return when (option.source) {
            BibleTranslationSource.API_BIBLE -> loadLicensedViaProxy(
                translation = translation,
                book = book,
                chapter = chapter,
                label = option.chipLabel,
            )
            BibleTranslationSource.OPEN_BIBLE -> loadOpenBibleWithOptionalProxy(
                translation = translation,
                book = book,
                chapter = chapter,
            )
        }
    }

    private suspend fun loadOpenBibleWithOptionalProxy(
        translation: String,
        book: String,
        chapter: Int,
    ): Result<BibleChapterContent> {
        if (bibleProxyConfig.isConfigured) {
            val viaProxy = bibleProxyClient.loadChapter(translation, book, chapter)
            if (viaProxy.isSuccess) {
                val result = viaProxy.getOrThrow()
                if (!paragraphsLackVerseMarkers(result.blocks)) {
                    return Result.success(BibleChapterContent(result.reference, result.blocks))
                }
            }
        }
        return loadOpenBibleChapter(translation, book, chapter)
    }

    private suspend fun loadLicensedViaProxy(
        translation: String,
        book: String,
        chapter: Int,
        label: String,
    ): Result<BibleChapterContent> {
        bibleProxyConfig.missingPieceMessage?.let { hint ->
            return Result.failure(
                IllegalStateException(
                    "$hint Licensed translations ($label) load through the Morning Guard Bible proxy.",
                ),
            )
        }
        return bibleProxyClient.loadChapter(translation, book, chapter).map { result ->
            BibleChapterContent(result.reference, result.blocks)
        }
    }

    private fun paragraphsLackVerseMarkers(blocks: List<BiblePassageBlock>): Boolean {
        val text = blocks
            .filterIsInstance<BiblePassageBlock.Paragraph>()
            .joinToString("\n") { it.text }
        if (text.isBlank()) return true
        return !Regex("""(\[\d+\]|\d+\.)""").containsMatchIn(text)
    }

    private suspend fun loadOpenBibleChapter(
        translation: String,
        book: String,
        chapter: Int,
    ): Result<BibleChapterContent> {
        val fallbackReference = "$book $chapter"
        return try {
            val bookAndChapter = "${book.lowercase().replace(' ', '+')}+$chapter"
            val response = openBibleApi.getChapter(bookAndChapter, translation)
            val body = when {
                response.verses.isNotEmpty() ->
                    response.verses.joinToString("\n") { verse ->
                        val text = verse.text.trim().replace('\n', ' ')
                        "${verse.verse}. $text"
                    }
                else -> response.plainText?.trim().orEmpty()
            }
            val reference = response.reference.ifBlank { fallbackReference }
            val verseText = body.ifBlank { "(content unavailable offline)" }
            Result.success(
                BiblePassageFormatter.parseChapterBody("$reference\n\n$verseText", fallbackReference),
            )
        } catch (e: retrofit2.HttpException) {
            Result.failure(e)
        } catch (e: java.io.IOException) {
            Result.failure(e)
        }
    }
}
