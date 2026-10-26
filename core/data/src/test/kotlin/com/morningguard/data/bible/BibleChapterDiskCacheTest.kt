package com.morningguard.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.morningguard.domain.bible.BibleChapterCachePolicy
import com.morningguard.domain.bible.BibleChapterContent
import com.morningguard.domain.bible.BiblePassageBlock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BibleChapterDiskCacheTest {

    private lateinit var cache: BibleChapterDiskCache

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cache = BibleChapterDiskCache(context, Json { ignoreUnknownKeys = true })
    }

    @Test
    fun `stores and returns chapter until ttl expires`() {
        val key = BibleChapterKey("web", "John", 3)
        val content = BibleChapterContent(
            reference = "John 3",
            blocks = listOf(BiblePassageBlock.Paragraph("1. In the beginning")),
        )
        kotlinx.coroutines.test.runTest {
            cache.put(key, content)
            assertEquals(content.reference, cache.get(key)?.reference)
        }
    }

    @Test
    fun `drops expired licensed entries`() {
        val key = BibleChapterKey("niv", "John", 3)
        val content = BibleChapterContent("John 3", listOf(BiblePassageBlock.Paragraph("1. text")))
        kotlinx.coroutines.test.runTest {
            cache.put(key, content)
            val dir = ApplicationProvider.getApplicationContext<Context>().cacheDir.resolve("bible-chapters")
            val file = dir.listFiles()?.singleOrNull()
            requireNotNull(file)
            val staleMs = System.currentTimeMillis() - BibleChapterCachePolicy.LICENSED_TTL_MS - 1_000L
            val patched = file.readText().replaceFirst(
                Regex(""""cachedAtEpochMs":\s*\d+"""),
                """"cachedAtEpochMs":$staleMs""",
            )
            file.writeText(patched)
            assertNull(cache.get(key))
        }
    }
}
