package com.morningguard.data.bible

import com.morningguard.domain.bible.BibleChapterContent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BibleChapterMemoryCache @Inject constructor() {
    private val maxEntries = 16
    private val lock = Any()
    private val entries = object : LinkedHashMap<String, BibleChapterContent>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BibleChapterContent>?): Boolean =
            size > maxEntries
    }

    fun get(key: BibleChapterKey): BibleChapterContent? = synchronized(lock) {
        entries[key.cacheId()]
    }

    fun put(key: BibleChapterKey, content: BibleChapterContent) {
        synchronized(lock) {
            entries[key.cacheId()] = content
        }
    }

    fun contains(key: BibleChapterKey): Boolean = synchronized(lock) {
        entries.containsKey(key.cacheId())
    }
}
