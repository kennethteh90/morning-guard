package com.morningguard.domain.bible

/** Aligns with Workers KV licensed TTL (`workers/bible-cache/src/cache-policy.ts`). */
object BibleChapterCachePolicy {
    const val LICENSED_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000
    const val OPEN_TTL_MS: Long = 365L * 24 * 60 * 60 * 1000

    fun ttlMsForTranslation(translationId: String): Long {
        val option = BibleTranslations.find(translationId)
        return if (option?.usesApiBible == true) LICENSED_TTL_MS else OPEN_TTL_MS
    }
}
