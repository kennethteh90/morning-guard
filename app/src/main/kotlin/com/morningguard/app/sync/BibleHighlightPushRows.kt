package com.morningguard.app.sync

import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.bible.BibleHighlightColors
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class BibleHighlightInsertRow(
    @SerialName("user_id") val userId: String,
    @SerialName("client_id") val clientId: Long,
    @SerialName("translation_id") val translationId: String,
    val book: String,
    val chapter: Int,
    val label: String,
    val excerpt: String,
    val verse: Int? = null,
    @SerialName("verse_end") val verseEnd: Int? = null,
    @SerialName("verse_numbers") val verseNumbers: String? = null,
    @SerialName("color_key") val colorKey: String,
    val note: String? = null,
    @SerialName("selection_start") val selectionStart: Int? = null,
    @SerialName("selection_end") val selectionEnd: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
internal data class BibleHighlightUpdateRow(
    val id: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("client_id") val clientId: Long? = null,
    @SerialName("translation_id") val translationId: String,
    val book: String,
    val chapter: Int,
    val label: String,
    val excerpt: String,
    val verse: Int? = null,
    @SerialName("verse_end") val verseEnd: Int? = null,
    @SerialName("verse_numbers") val verseNumbers: String? = null,
    @SerialName("color_key") val colorKey: String,
    val note: String? = null,
    @SerialName("selection_start") val selectionStart: Int? = null,
    @SerialName("selection_end") val selectionEnd: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

internal sealed class BibleHighlightPushPayload {
    data class InsertNew(val row: BibleHighlightInsertRow) : BibleHighlightPushPayload()
    data class UpdateExisting(val row: BibleHighlightUpdateRow) : BibleHighlightPushPayload()
}

internal object BibleHighlightPushRows {
    private val json = Json { explicitNulls = false }

    fun encodeInsertRow(row: BibleHighlightInsertRow): String = json.encodeToString(row)

    fun encodeUpdateRow(row: BibleHighlightUpdateRow): String = json.encodeToString(row)

    fun fromWireRow(
        userId: String,
        highlight: BibleHighlight,
        noteForRow: String,
        createdAtIso: String,
        remoteServerId: Long?,
    ): BibleHighlightPushPayload? {
        val clientId = highlight.id.takeIf { it > 0 } ?: return null
        val common = CommonFields(
            userId = userId,
            clientId = clientId,
            translationId = highlight.translationId,
            book = highlight.book,
            chapter = highlight.chapter,
            label = highlight.label,
            excerpt = highlight.excerpt,
            verse = highlight.verse,
            verseEnd = highlight.verseEnd,
            verseNumbers = highlight.verseNumbers,
            colorKey = highlight.colorKey.ifBlank { BibleHighlightColors.DEFAULT_KEY },
            note = noteForRow,
            selectionStart = highlight.selectionStart,
            selectionEnd = highlight.selectionEnd,
            createdAt = createdAtIso,
        )
        return if (remoteServerId != null && remoteServerId > 0) {
            BibleHighlightPushPayload.UpdateExisting(common.toUpdateRow(remoteServerId))
        } else {
            BibleHighlightPushPayload.InsertNew(common.toInsertRow())
        }
    }

    private data class CommonFields(
        val userId: String,
        val clientId: Long,
        val translationId: String,
        val book: String,
        val chapter: Int,
        val label: String,
        val excerpt: String,
        val verse: Int?,
        val verseEnd: Int?,
        val verseNumbers: String?,
        val colorKey: String,
        val note: String,
        val selectionStart: Int?,
        val selectionEnd: Int?,
        val createdAt: String,
    ) {
        fun toInsertRow(): BibleHighlightInsertRow = BibleHighlightInsertRow(
            userId = userId,
            clientId = clientId,
            translationId = translationId,
            book = book,
            chapter = chapter,
            label = label,
            excerpt = excerpt,
            verse = verse,
            verseEnd = verseEnd,
            verseNumbers = verseNumbers,
            colorKey = colorKey,
            note = note,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            createdAt = createdAt,
        )

        fun toUpdateRow(serverId: Long): BibleHighlightUpdateRow = BibleHighlightUpdateRow(
            id = serverId,
            userId = userId,
            clientId = clientId,
            translationId = translationId,
            book = book,
            chapter = chapter,
            label = label,
            excerpt = excerpt,
            verse = verse,
            verseEnd = verseEnd,
            verseNumbers = verseNumbers,
            colorKey = colorKey,
            note = note,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            createdAt = createdAt,
        )
    }
}
