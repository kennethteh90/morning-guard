package com.morningguard.data.bible

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class BibleProxyBlockDto(
    val type: String,
    val text: String,
)

@Serializable
data class BibleProxyChapterResponse(
    val reference: String = "",
    val body: String = "",
    val blocks: List<BibleProxyBlockDto>? = null,
)

interface BibleProxyApi {
    @GET("v1/chapter")
    suspend fun getChapter(
        @Query("translation") translation: String,
        @Query("book") book: String,
        @Query("chapter") chapter: Int,
    ): BibleProxyChapterResponse
}
