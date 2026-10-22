package com.morningguard.data.backup

import com.morningguard.data.security.NoteCryptoState
import com.morningguard.data.settings.GoalProgressStore
import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.model.UserSettings
import kotlinx.serialization.Serializable

const val LOCAL_BACKUP_SCHEMA_VERSION = 2

@Serializable
data class MorningGuardBackupPayload(
    val schemaVersion: Int = LOCAL_BACKUP_SCHEMA_VERSION,
    val exportedAtEpochMs: Long,
    val settings: UserSettings,
    val goalProgress: GoalProgressStore,
    /** Password-wrapped note DEK (no Keystore wraps — restored device re-wraps after unlock). */
    val noteCrypto: NoteCryptoState? = null,
    val highlights: List<BibleHighlightBackupRow>,
)

@Serializable
data class BibleHighlightBackupRow(
    val translationId: String,
    val book: String,
    val chapter: Int,
    val label: String,
    val excerpt: String,
    val verse: Int? = null,
    val verseEnd: Int? = null,
    val verseNumbers: String? = null,
    val colorKey: String,
    val note: String = "",
    val selectionStart: Int? = null,
    val selectionEnd: Int? = null,
    val createdAtEpochMs: Long,
)

fun BibleHighlight.toBackupRow(): BibleHighlightBackupRow = BibleHighlightBackupRow(
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

fun BibleHighlightBackupRow.toDomain(): BibleHighlight = BibleHighlight(
    id = 0,
    remoteId = 0,
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

@Serializable
data class EncryptedLocalBackupFile(
    val schemaVersion: Int = LOCAL_BACKUP_SCHEMA_VERSION,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
)
