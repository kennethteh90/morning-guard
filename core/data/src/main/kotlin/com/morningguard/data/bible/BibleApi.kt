package com.morningguard.data.bible

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class BibleChapterResponse(
    val reference: String = "",
    val verses: List<BibleVerse> = emptyList(),
    @SerialName("text") val plainText: String? = null,
)

@Serializable
data class BibleVerse(
    val verse: Int = 0,
    val text: String = "",
)

interface BibleApi {
    @GET("{bookAndChapter}")
    suspend fun getChapter(
        @Path("bookAndChapter") bookAndChapter: String,
        @retrofit2.http.Query("translation") translation: String,
    ): BibleChapterResponse
}
