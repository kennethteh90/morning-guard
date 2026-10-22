package com.morningguard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.morningguard.domain.bible.BibleHighlight

@Entity(tableName = "bible_highlights")
data class BibleHighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Long? = null,
    val translationId: String,
    val book: String,
    val chapter: Int,
    val label: String,
    val excerpt: String,
    val verse: Int? = null,
    val verseEnd: Int? = null,
    val verseNumbers: String? = null,
    val colorKey: String = "yellow",
    val note: String = "",
    val selectionStart: Int? = null,
    val selectionEnd: Int? = null,
    val createdAtEpochMs: Long,
)

fun BibleHighlightEntity.toDomain(): BibleHighlight = BibleHighlight(
    id = id,
    remoteId = remoteId ?: 0L,
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
    createdAtEpochMs = createdAtEpochMs,
)

fun BibleHighlight.toEntity(): BibleHighlightEntity = BibleHighlightEntity(
    id = id,
    remoteId = remoteId.takeIf { it > 0 },
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
    createdAtEpochMs = createdAtEpochMs,
)
