package com.morningguard.data.bible

import com.morningguard.data.local.dao.BibleHighlightDao
import com.morningguard.data.local.entity.BibleHighlightEntity
import com.morningguard.data.local.entity.toDomain
import com.morningguard.data.local.entity.toEntity
import com.morningguard.data.security.HighlightNoteCipher
import com.morningguard.domain.bible.BibleHighlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BibleHighlightRepository @Inject constructor(
    private val dao: BibleHighlightDao,
    private val noteCipher: HighlightNoteCipher,
) {
    val highlights: Flow<List<BibleHighlight>> =
        dao.observeAll().map { list -> list.map { it.toDomainDecrypted() } }

    suspend fun listAll(): List<BibleHighlight> =
        dao.listAll().map { it.toDomainDecrypted() }

    suspend fun save(highlight: BibleHighlight): Long =
        dao.insert(highlight.withEncryptedNote().copy(id = 0).toEntity())

    suspend fun listAllStoredForBackup(): List<BibleHighlight> =
        dao.listAll().map { it.toDomainDecrypted() }

    suspend fun importStoredHighlight(highlight: BibleHighlight) {
        save(highlight.copy(id = 0, remoteId = 0))
    }

    suspend fun reencryptAllStoredNotes() {
        for (entity in dao.listAll()) {
            val plain = noteCipher.decryptFromStorage(entity.note)
            val stored = noteCipher.encryptForStorage(plain)
            if (stored != entity.note) {
                dao.update(entity.copy(note = stored))
            }
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
        for (remote in remoteHighlights) {
            if (remote.remoteId <= 0) continue
            val existingByRemote = dao.findByRemoteId(remote.remoteId)
            if (existingByRemote != null) {
                dao.update(
                    existingByRemote.copy(
                        translationId = remote.translationId,
                        book = remote.book,
                        chapter = remote.chapter,
                        label = remote.label,
                        excerpt = remote.excerpt,
                        verse = remote.verse,
                        verseEnd = remote.verseEnd,
                        verseNumbers = remote.verseNumbers,
                        colorKey = remote.colorKey,
                        note = remote.note,
                        selectionStart = remote.selectionStart,
                        selectionEnd = remote.selectionEnd,
                        createdAtEpochMs = remote.createdAtEpochMs,
                    ),
                )
                continue
            }
            val localId = remote.id.takeIf { it > 0 }
            if (localId != null) {
                val local = dao.listAll().find { it.id == localId }
                if (local != null) {
                    dao.update(
                        local.copy(
                            remoteId = remote.remoteId,
                            translationId = remote.translationId,
                            book = remote.book,
                            chapter = remote.chapter,
                            label = remote.label,
                            excerpt = remote.excerpt,
                            verse = remote.verse,
                            verseEnd = remote.verseEnd,
                            verseNumbers = remote.verseNumbers,
                            colorKey = remote.colorKey,
                            note = remote.note,
                            selectionStart = remote.selectionStart,
                            selectionEnd = remote.selectionEnd,
                        ),
                    )
                    continue
                }
            }
            dao.insert(
                remote.copy(id = 0).toEntity().copy(remoteId = remote.remoteId),
            )
        }
    }
}
