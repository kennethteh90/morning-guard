package com.morningguard.data.bible

import com.morningguard.data.local.dao.BibleHighlightDao
import com.morningguard.data.local.entity.BibleHighlightEntity
import com.morningguard.data.local.entity.toDomain
import com.morningguard.data.local.entity.toEntity
import com.morningguard.data.security.HighlightNoteCipher
import com.morningguard.data.security.NoteContentCipher
import com.morningguard.data.security.NoteEncryptionRepository
import com.morningguard.domain.bible.BibleHighlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("TooManyFunctions")
@Singleton
class BibleHighlightRepository @Inject constructor(
    private val dao: BibleHighlightDao,
    private val noteCipher: HighlightNoteCipher,
    private val noteEncryptionRepository: NoteEncryptionRepository,
) {
    val highlights: Flow<List<BibleHighlight>> =
        dao.observeAll().map { list -> list.map { it.toDomainDecrypted() } }

    suspend fun listAll(): List<BibleHighlight> =
        dao.listAll().map { it.toDomainDecrypted() }

    suspend fun save(highlight: BibleHighlight): Long {
        noteEncryptionRepository.ensureDekReady().getOrThrow()
        return dao.insert(highlight.withEncryptedNote().copy(id = 0).toEntity())
    }

    suspend fun listAllStoredForBackup(): List<BibleHighlight> =
        dao.listAll().map { it.toDomainDecrypted() }

    suspend fun importStoredHighlight(highlight: BibleHighlight) {
        save(highlight.copy(id = 0, remoteId = 0))
    }

    suspend fun reencryptAllStoredNotes() {
        noteEncryptionRepository.ensureDekReady().getOrThrow()
        for (entity in dao.listAll()) {
            val plain = when {
                NoteContentCipher.isCiphertext(entity.note) -> noteCipher.decryptFromStorage(entity.note)
                else -> entity.note
            }
            val stored = noteCipher.encryptForStorage(plain)
            if (stored != entity.note) {
                dao.update(entity.copy(note = stored))
            }
        }
    }

    suspend fun migrateLegacyPlaintextNotes() {
        noteEncryptionRepository.ensureDekReady().getOrThrow()
        for (entity in dao.listAll()) {
            if (!NoteContentCipher.isLegacyPlaintext(entity.note)) continue
            val stored = noteCipher.encryptForStorage(entity.note)
            dao.update(entity.copy(note = stored))
        }
    }

    private fun BibleHighlightEntity.toDomainDecrypted(): BibleHighlight =
        toDomain().copy(note = noteCipher.decryptFromStorage(note))

    private fun BibleHighlight.withEncryptedNote(): BibleHighlight =
        copy(note = noteCipher.encryptForStorage(note))

    suspend fun remove(highlight: BibleHighlight) {
        when {
            highlight.id > 0 -> dao.deleteById(highlight.id)
            highlight.remoteId > 0 -> dao.deleteByRemoteId(highlight.remoteId)
        }
        if (highlight.verse != null) {
            val duplicates = dao.listAll().filter { entity ->
                entity.translationId == highlight.translationId &&
                    entity.book == highlight.book &&
                    entity.chapter == highlight.chapter &&
                    entity.verse == highlight.verse &&
                    (highlight.remoteId <= 0 || entity.remoteId == highlight.remoteId)
            }
            for (entity in duplicates) {
                dao.deleteById(entity.id)
            }
        }
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    /** Drop local rows tied to remote ids that no longer exist, then merge remote rows. */
    suspend fun reconcileWithRemote(remoteHighlights: List<BibleHighlight>) {
        val remoteIds = remoteHighlights.map { it.remoteId }.filter { it > 0 }.toSet()
        for (local in dao.listAll()) {
            val rid = local.remoteId ?: continue
            if (rid > 0 && rid !in remoteIds) {
                dao.deleteByRemoteId(rid)
            }
        }
        mergeFromRemote(remoteHighlights)
    }

    private suspend fun mergeFromRemote(remoteHighlights: List<BibleHighlight>) {
        noteEncryptionRepository.ensureDekReady().getOrThrow()
        remoteHighlights.filter { it.remoteId > 0 }.forEach { mergeOneRemote(it) }
    }

    private suspend fun mergeOneRemote(remote: BibleHighlight) {
        val noteStored = when {
            NoteContentCipher.isLegacyPlaintext(remote.note) ->
                noteCipher.encryptForStorage(remote.note)
            else -> remote.note
        }
        val remoteNormalized = remote.copy(note = noteStored)
        val existingByRemote = dao.findByRemoteId(remote.remoteId)
        if (existingByRemote != null) {
            dao.update(
                existingByRemote.copy(
                    translationId = remoteNormalized.translationId,
                    book = remoteNormalized.book,
                    chapter = remoteNormalized.chapter,
                    label = remoteNormalized.label,
                    excerpt = remoteNormalized.excerpt,
                    verse = remoteNormalized.verse,
                    verseEnd = remoteNormalized.verseEnd,
                    verseNumbers = remoteNormalized.verseNumbers,
                    colorKey = remoteNormalized.colorKey,
                    note = remoteNormalized.note,
                    selectionStart = remote.selectionStart,
                    selectionEnd = remote.selectionEnd,
                    createdAtEpochMs = remoteNormalized.createdAtEpochMs,
                ),
            )
            return
        }
        val localId = remoteNormalized.id.takeIf { it > 0 }
        if (localId != null) {
            val local = dao.listAll().find { it.id == localId }
            if (local != null) {
                dao.update(
                    local.copy(
                        remoteId = remoteNormalized.remoteId,
                        translationId = remoteNormalized.translationId,
                        book = remoteNormalized.book,
                        chapter = remoteNormalized.chapter,
                        label = remoteNormalized.label,
                        excerpt = remoteNormalized.excerpt,
                        verse = remoteNormalized.verse,
                        verseEnd = remoteNormalized.verseEnd,
                        verseNumbers = remoteNormalized.verseNumbers,
                        colorKey = remoteNormalized.colorKey,
                        note = remoteNormalized.note,
                        selectionStart = remote.selectionStart,
                        selectionEnd = remote.selectionEnd,
                    ),
                )
                return
            }
        }
        dao.insert(
            remoteNormalized.copy(id = 0).toEntity().copy(remoteId = remoteNormalized.remoteId),
        )
    }
}
