package com.morningguard.data.backup

import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.data.security.NoteCryptoState
import com.morningguard.data.security.NoteEncryptionRepository
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.model.DataStorageMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBackupRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bibleHighlightRepository: BibleHighlightRepository,
    private val noteEncryptionRepository: NoteEncryptionRepository,
    private val json: Json,
) {

    suspend fun buildPayload(): MorningGuardBackupPayload {
        val settings = settingsRepository.currentSettings()
        val goalProgress = settingsRepository.currentGoalStore()
        val crypto = noteEncryptionRepository.cloudExportState()
        val highlights = bibleHighlightRepository.listAllStoredForBackup().map { it.toBackupRow() }
        return MorningGuardBackupPayload(
            exportedAtEpochMs = System.currentTimeMillis(),
            settings = settings.copy(onboardingCompleted = true),
            goalProgress = goalProgress,
            noteCrypto = crypto,
            highlights = highlights,
        )
    }

    suspend fun exportEncrypted(password: CharArray, output: OutputStream) {
        require(password.size >= 8) { "Backup password must be at least 8 characters." }
        val plaintext = json.encodeToString(buildPayload()).encodeToByteArray()
        val encrypted = LocalBackupCipher.encrypt(plaintext, password)
        output.write(json.encodeToString(encrypted).encodeToByteArray())
        output.flush()
    }

    suspend fun importEncrypted(password: CharArray, input: InputStream, replaceExisting: Boolean = true) {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) error("Backup file is empty.")
        val envelope = json.decodeFromString(EncryptedLocalBackupFile.serializer(), text)
        if (envelope.schemaVersion > LOCAL_BACKUP_SCHEMA_VERSION) {
            error("Backup was made with a newer app version. Update Morning Guard and try again.")
        }
        val plaintext = try {
            LocalBackupCipher.decrypt(envelope, password).decodeToString()
        } catch (_: Exception) {
            error("Wrong backup password or corrupted backup file.")
        }
        val payload = json.decodeFromString(MorningGuardBackupPayload.serializer(), plaintext)
        if (payload.schemaVersion < 2) {
            error("This backup format is no longer supported. Create a new backup with the current app.")
        }
        applyPayload(payload, replaceExisting)
    }

    private suspend fun applyPayload(payload: MorningGuardBackupPayload, replaceExisting: Boolean) {
        if (!replaceExisting) {
            error("Merge restore is not supported yet. Use replace.")
        }
        val restoredSettings = payload.settings.copy(
            dataStorageMode = DataStorageMode.LOCAL_ONLY,
            onboardingCompleted = true,
            syncBoundUserId = null,
        )
        settingsRepository.updateSettings { _ -> restoredSettings }
        settingsRepository.updateGoalProgress { payload.goalProgress }
        noteEncryptionRepository.lock()
        val crypto = payload.noteCrypto
        if (crypto != null && crypto.enabled) {
            noteEncryptionRepository.applyCloudWrappedDek(crypto.portableForBackup()).getOrThrow()
        } else {
            noteEncryptionRepository.resetStoredCrypto()
        }
        bibleHighlightRepository.clearAll()
        for (row in payload.highlights) {
            bibleHighlightRepository.importStoredHighlight(row.toDomain())
        }
    }

    private fun NoteCryptoState.portableForBackup(): NoteCryptoState =
        copy(
            keystoreWrappedDekBase64 = null,
            keystoreWrapIvBase64 = null,
        )
}
