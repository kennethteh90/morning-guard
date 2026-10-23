package com.morningguard.data.bible

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class ApiBibleListResponse(
    val data: List<ApiBibleSummary> = emptyList(),
)

@Serializable
data class ApiBibleSummary(
    val id: String = "",
    val abbreviation: String = "",
    @SerialName("abbreviationLocal") val abbreviationLocal: String = "",
    val name: String = "",
    @SerialName("nameLocal") val nameLocal: String = "",
)

@Serializable
data class ApiBibleChapterResponse(
    val data: ApiBibleChapterData? = null,
)

@Serializable
data class ApiBibleChapterData(
    val id: String = "",
    val content: String = "",
    val reference: String = "",
)

interface ApiBibleApi {
    @GET("bibles")
    suspend fun listBibles(): ApiBibleListResponse

    @GET("bibles/{bibleId}/chapters/{chapterId}")
    suspend fun getChapter(
        @Path("bibleId") bibleId: String,
        @Path("chapterId") chapterId: String,
        @Query("content-type") contentType: String = "text",
    ): ApiBibleChapterResponse
}
