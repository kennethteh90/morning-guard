package com.morningguard.app.sync

import com.morningguard.app.supabase.SupabaseClientProvider
import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.data.monitoring.ErrorReporter
import com.morningguard.data.security.HighlightNoteCipher
import com.morningguard.data.security.HighlightSyncNotePolicy
import com.morningguard.data.security.NoteContentCipher
import com.morningguard.data.security.NoteCryptoKeyManager
import com.morningguard.data.security.NoteCryptoState
import com.morningguard.data.security.NoteEncryptionRepository
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.bible.BibleHighlightColors
import com.morningguard.domain.history.MorningDayOutcome
import com.morningguard.domain.history.MorningDayRecord
import com.morningguard.domain.model.DataStorageMode
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class MorningDayRecordRow(
    @SerialName("user_id") val userId: String,
    @SerialName("date_key") val dateKey: String,
    val outcome: String,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("time_on_goal_seconds") val timeOnGoalSeconds: Int = 0,
)

@Serializable
private data class UserNoteCryptoRow(
    @SerialName("user_id") val userId: String,
    @SerialName("password_wrapped_dek") val passwordWrappedDek: String,
    @SerialName("salt_base64") val saltBase64: String,
    @SerialName("wrap_iv_base64") val wrapIvBase64: String,
    @SerialName("kdf_iterations") val kdfIterations: Int = NoteCryptoKeyManager.PBKDF2_ITERATIONS,
)

@Serializable
private data class BibleHighlightRow(
    val id: Long? = null,
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

@Singleton
class UserDataSyncService @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val settingsRepository: SettingsRepository,
    private val bibleHighlightRepository: BibleHighlightRepository,
    private val noteEncryptionRepository: NoteEncryptionRepository,
    private val noteCipher: HighlightNoteCipher,
    private val errorReporter: ErrorReporter,
) {

    suspend fun syncAllIfSignedIn(): Result<UserDataSyncReport> {
        if (settingsRepository.currentSettings().dataStorageMode == DataStorageMode.LOCAL_ONLY) {
            return Result.success(
                UserDataSyncReport.single(
                    SyncPhaseOutcome.Skipped("syncAll", "Local-only storage mode"),
                ),
            )
        }
        val client = clientProvider.client
            ?: return preconditionFailure(SyncPreconditionFailure.NotConfigured())
        if (client.auth.currentSessionOrNull() == null) {
            return preconditionFailure(SyncPreconditionFailure.NotSignedIn())
        }
        noteEncryptionRepository.ensureDekReady().onFailure { error ->
            recordSyncFailure("note_crypto ensureDek", error)
        }
        bibleHighlightRepository.migrateLegacyPlaintextNotes()
        val report = UserDataSyncReport.merge(
            listOf(
                syncMorningHistory(client),
                syncNoteCrypto(client),
                syncHighlights(client),
            ),
        )
        finishSyncRun("syncAll", report)
        return report.toResult()
    }

    suspend fun syncMorningHistoryIfSignedIn(): Result<UserDataSyncReport> {
        if (settingsRepository.currentSettings().dataStorageMode == DataStorageMode.LOCAL_ONLY) {
            return Result.success(
                UserDataSyncReport.single(
                    SyncPhaseOutcome.Skipped("syncMorningHistory", "Local-only storage mode"),
                ),
            )
        }
        val client = clientProvider.client
            ?: return preconditionFailure(SyncPreconditionFailure.NotConfigured())
        if (client.auth.currentSessionOrNull() == null) {
            return preconditionFailure(SyncPreconditionFailure.NotSignedIn())
        }
        val report = syncMorningHistory(client)
        finishSyncRun("syncMorningHistory", report)
        return report.toResult()
    }

    suspend fun syncHighlightsIfSignedIn(): Result<UserDataSyncReport> {
        if (settingsRepository.currentSettings().dataStorageMode == DataStorageMode.LOCAL_ONLY) {
            return Result.success(
                UserDataSyncReport.single(
                    SyncPhaseOutcome.Skipped("syncHighlights", "Local-only storage mode"),
                ),
            )
        }
        val client = clientProvider.client
            ?: return preconditionFailure(SyncPreconditionFailure.NotConfigured())
        if (client.auth.currentSessionOrNull() == null) {
            return preconditionFailure(SyncPreconditionFailure.NotSignedIn())
        }
        noteEncryptionRepository.ensureDekReady()
        bibleHighlightRepository.migrateLegacyPlaintextNotes()
        val report = syncHighlights(client)
        finishSyncRun("syncHighlights", report)
        return report.toResult()
    }

    /**
     * Removes locally always. Attempts remote delete when signed in; returns [Result.failure] if
     * remote delete fails (local row is still removed).
     */
    suspend fun deleteHighlight(highlight: BibleHighlight): Result<Unit> {
        val client = clientProvider.client
        if (client == null) {
            SyncLog.skipped("bible_highlights delete", "Supabase not configured")
            bibleHighlightRepository.remove(highlight)
            return Result.success(Unit)
        }
        if (client.auth.currentSessionOrNull() == null) {
            SyncLog.skipped("bible_highlights delete", "not signed in")
            bibleHighlightRepository.remove(highlight)
            return Result.success(Unit)
        }
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            SyncLog.skipped("bible_highlights delete", "missing user id")
            bibleHighlightRepository.remove(highlight)
            return Result.success(Unit)
        }

        val remoteResult = runCatching {
            when {
                highlight.remoteId > 0 -> {
                    client.postgrest.from(TABLE_HIGHLIGHTS).delete {
                        filter {
                            eq("id", highlight.remoteId)
                            eq("user_id", userId)
                        }
                    }
                }
                highlight.id > 0 -> {
                    client.postgrest.from(TABLE_HIGHLIGHTS).delete {
                        filter {
                            eq("client_id", highlight.id)
                            eq("user_id", userId)
                        }
                    }
                }
                else -> {
                    val verse = highlight.verse
                        ?: error("Cannot delete on server: highlight has no remote id or verse")
                    client.postgrest.from(TABLE_HIGHLIGHTS).delete {
                        filter {
                            eq("user_id", userId)
                            eq("translation_id", highlight.translationId)
                            eq("book", highlight.book)
                            eq("chapter", highlight.chapter)
                            eq("verse", verse)
                        }
                    }
                }
            }
        }

        bibleHighlightRepository.remove(highlight)

        remoteResult.onSuccess {
            SyncLog.ok("bible_highlights delete", "local+remote highlight removed")
        }.onFailure { error ->
            recordSyncFailure("bible_highlights delete", error)
        }

        return remoteResult.map { }
    }

    private suspend fun syncMorningHistory(client: SupabaseClient): UserDataSyncReport {
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            val outcome = SyncPhaseOutcome.Skipped(PHASE_MORNING_PULL, "missing user id")
            SyncLog.skipped(PHASE_MORNING_PULL, "missing user id")
            return UserDataSyncReport.single(outcome)
        }

        val pullResult = runCatching {
            client.postgrest.from(TABLE_MORNING_DAYS)
                .select()
                .decodeList<MorningDayRecordRow>()
        }
        val remoteRows = pullResult.getOrElse { error ->
            recordSyncFailure(PHASE_MORNING_PULL, error)
            return UserDataSyncReport.single(SyncPhaseOutcome.Failed(PHASE_MORNING_PULL, error))
        }

        val domainRows = remoteRows.mapNotNull { it.toDomain() }
        val parseSkipped = remoteRows.size - domainRows.size
        if (parseSkipped > 0) {
            SyncLog.info("$PHASE_MORNING_PULL ignored $parseSkipped row(s) with invalid outcome")
        }

        settingsRepository.mergeMorningHistoryFromRemote(domainRows)
        SyncLog.ok(PHASE_MORNING_PULL, "merged ${domainRows.size} row(s)")

        val local = settingsRepository.currentGoalStore().morningHistory
        if (local.isEmpty()) {
            SyncLog.skipped(PHASE_MORNING_PUSH, "no local history")
            return UserDataSyncReport(
                listOf(
                    SyncPhaseOutcome.Ok(PHASE_MORNING_PULL, "${domainRows.size} remote row(s)"),
                    SyncPhaseOutcome.Skipped(PHASE_MORNING_PUSH, "no local history"),
                ),
            )
        }

        val rows = local.map { record ->
            MorningDayRecordRow(
                userId = userId,
                dateKey = record.dateKey,
                outcome = record.outcome.name,
                recordedAt = instantToIso(record.recordedAtEpochMs),
                timeOnGoalSeconds = record.timeOnGoalSeconds,
            )
        }

        val pushResult = runCatching {
            client.postgrest.from(TABLE_MORNING_DAYS).upsert(rows) {
                onConflict = "user_id,date_key"
            }
        }

        return pushResult.fold(
            onSuccess = {
                SyncLog.ok(PHASE_MORNING_PUSH, "upserted ${rows.size} row(s)")
                UserDataSyncReport(
                    listOf(
                        SyncPhaseOutcome.Ok(PHASE_MORNING_PULL, "${domainRows.size} remote row(s)"),
                        SyncPhaseOutcome.Ok(PHASE_MORNING_PUSH, "${rows.size} local row(s)"),
                    ),
                )
            },
            onFailure = { error ->
                recordSyncFailure(PHASE_MORNING_PUSH, error)
                UserDataSyncReport(
                    listOf(
                        SyncPhaseOutcome.Ok(PHASE_MORNING_PULL, "${domainRows.size} remote row(s)"),
                        SyncPhaseOutcome.Failed(PHASE_MORNING_PUSH, error),
                    ),
                )
            },
        )
    }

    private suspend fun syncHighlights(client: SupabaseClient): UserDataSyncReport {
        val outcomes = mutableListOf<SyncPhaseOutcome>()
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            SyncLog.skipped(PHASE_HIGHLIGHTS, "missing user id")
            return UserDataSyncReport.single(SyncPhaseOutcome.Skipped(PHASE_HIGHLIGHTS, "missing user id"))
        }

        val bound = settingsRepository.boundUserId()
        if (bound != null && bound != userId) {
            SyncLog.info("Highlights bound user changed ($bound → $userId); clearing local highlights/history")
            bibleHighlightRepository.clearAll()
            settingsRepository.clearMorningHistory()
        }
        settingsRepository.setBoundUserId(userId)

        val pullOutcome = pullHighlights(client)
        val remoteNoteByServerId = pullOutcome?.remoteNoteByServerId ?: emptyMap()
        pullOutcome?.outcome?.let { outcomes += it }

        if (noteEncryptionRepository.requiresCloudRecovery()) {
            SyncLog.skipped(PHASE_HIGHLIGHTS_PUSH, "cloud recovery password required")
            outcomes += SyncPhaseOutcome.Skipped(
                PHASE_HIGHLIGHTS_PUSH,
                "enter recovery password before syncing notes",
            )
            return UserDataSyncReport(outcomes)
        }

        if (!SyncOwnership.shouldUploadLocalHighlights(bound, userId)) {
            SyncLog.skipped(PHASE_HIGHLIGHTS_PUSH, "local highlights owned by another account")
            outcomes += SyncPhaseOutcome.Skipped(
                PHASE_HIGHLIGHTS_PUSH,
                "not uploading — different bound user",
            )
            return UserDataSyncReport(outcomes)
        }

        val local = bibleHighlightRepository.listAll()
        if (local.isEmpty()) {
            SyncLog.skipped(PHASE_HIGHLIGHTS_PUSH, "no local highlights")
            outcomes += SyncPhaseOutcome.Skipped(PHASE_HIGHLIGHTS_PUSH, "no local highlights")
            return UserDataSyncReport(outcomes)
        }

        val toPush = buildHighlightPushRows(
            local = local,
            userId = userId,
            remoteNoteByServerId = remoteNoteByServerId,
            dekUnlocked = noteEncryptionRepository.isDekUnlocked(),
        )

        val pushResult = runCatching {
            client.postgrest.from(TABLE_HIGHLIGHTS).upsert(toPush) {
                onConflict = "id"
            }
        }

        outcomes += pushResult.fold(
            onSuccess = {
                SyncLog.ok(PHASE_HIGHLIGHTS_PUSH, "upserted ${toPush.size} row(s)")
                SyncPhaseOutcome.Ok(PHASE_HIGHLIGHTS_PUSH, "${toPush.size} local row(s)")
            },
            onFailure = { error ->
                recordSyncFailure(PHASE_HIGHLIGHTS_PUSH, error)
                SyncPhaseOutcome.Failed(PHASE_HIGHLIGHTS_PUSH, error)
            },
        )

        pullHighlights(client)?.outcome?.let { outcomes += it }

        return UserDataSyncReport(outcomes)
    }

    private fun buildHighlightPushRows(
        local: List<BibleHighlight>,
        userId: String,
        remoteNoteByServerId: Map<Long, String>,
        dekUnlocked: Boolean,
    ): List<BibleHighlightRow> = local.mapNotNull { highlight ->
        val remoteNote = highlight.remoteId.takeIf { it > 0 }?.let { remoteNoteByServerId[it] }
        val decision = HighlightSyncNotePolicy.resolve(
            localPlainNote = highlight.note,
            remoteNote = remoteNote,
            dekUnlocked = dekUnlocked,
        )
        if (decision is HighlightSyncNotePolicy.NotePushValue.OmitNote) {
            return@mapNotNull null
        }
        val wireNote = HighlightSyncNotePolicy.toWireValue(decision) { plain ->
            noteCipher.encryptForStorage(plain)
        }
        val noteForRow = when {
            wireNote != null -> wireNote
            remoteNote != null && NoteContentCipher.isCiphertext(remoteNote) -> remoteNote
            else -> ""
        }
        BibleHighlightRow(
            id = highlight.remoteId.takeIf { it > 0 },
            userId = userId,
            clientId = highlight.id.takeIf { it > 0 },
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
            createdAt = instantToIso(highlight.createdAtEpochMs),
        )
    }

    /** @return pull outcome, or null if pull was already recorded as failed and callers should stop. */
    private suspend fun syncNoteCrypto(client: SupabaseClient): UserDataSyncReport {
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            return UserDataSyncReport.single(
                SyncPhaseOutcome.Skipped(PHASE_NOTE_CRYPTO, "missing user id"),
            )
        }
        val outcomes = mutableListOf<SyncPhaseOutcome>()
        val pullResult = runCatching {
            client.postgrest.from(TABLE_NOTE_CRYPTO)
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeSingleOrNull<UserNoteCryptoRow>()
        }
        pullResult.onSuccess { remote ->
            if (remote != null) {
                val state = NoteCryptoState(
                    enabled = true,
                    passwordWrappedDekBase64 = remote.passwordWrappedDek,
                    passwordWrapSaltBase64 = remote.saltBase64,
                    passwordWrapIvBase64 = remote.wrapIvBase64,
                )
                noteEncryptionRepository.applyCloudWrappedDek(state)
                    .onFailure { error -> recordSyncFailure(PHASE_NOTE_CRYPTO_PULL, error) }
                noteEncryptionRepository.tryUnlockFromKeystore()
                outcomes += SyncPhaseOutcome.Ok(PHASE_NOTE_CRYPTO_PULL, "metadata pulled")
            } else {
                outcomes += SyncPhaseOutcome.Skipped(PHASE_NOTE_CRYPTO_PULL, "no remote metadata")
            }
        }.onFailure { error ->
            outcomes += SyncPhaseOutcome.Failed(PHASE_NOTE_CRYPTO_PULL, error)
        }

        val export = noteEncryptionRepository.cloudExportState()
        if (export != null) {
            val row = UserNoteCryptoRow(
                userId = userId,
                passwordWrappedDek = export.passwordWrappedDekBase64 ?: return UserDataSyncReport(outcomes),
                saltBase64 = export.passwordWrapSaltBase64 ?: return UserDataSyncReport(outcomes),
                wrapIvBase64 = export.passwordWrapIvBase64 ?: return UserDataSyncReport(outcomes),
                kdfIterations = NoteCryptoKeyManager.PBKDF2_ITERATIONS,
            )
            val pushResult = runCatching {
                client.postgrest.from(TABLE_NOTE_CRYPTO).upsert(row) {
                    onConflict = "user_id"
                }
            }
            outcomes += pushResult.fold(
                onSuccess = { SyncPhaseOutcome.Ok(PHASE_NOTE_CRYPTO_PUSH, "metadata pushed") },
                onFailure = { SyncPhaseOutcome.Failed(PHASE_NOTE_CRYPTO_PUSH, it) },
            )
        } else {
            outcomes += SyncPhaseOutcome.Skipped(PHASE_NOTE_CRYPTO_PUSH, "encryption not enabled locally")
        }
        return UserDataSyncReport(outcomes)
    }

    private data class HighlightPullResult(
        val outcome: SyncPhaseOutcome,
        val remoteNoteByServerId: Map<Long, String>,
    )

    private suspend fun pullHighlights(client: SupabaseClient): HighlightPullResult? {
        val pullResult = runCatching {
            client.postgrest.from(TABLE_HIGHLIGHTS)
                .select()
                .decodeList<BibleHighlightRow>()
        }
        val remoteRows = pullResult.getOrElse { error ->
            recordSyncFailure(PHASE_HIGHLIGHTS_PULL, error)
            return HighlightPullResult(
                SyncPhaseOutcome.Failed(PHASE_HIGHLIGHTS_PULL, error),
                emptyMap(),
            )
        }

        val remoteNoteByServerId = remoteRows.mapNotNull { row ->
            val id = row.id ?: return@mapNotNull null
            id to row.note.orEmpty()
        }.toMap()

        val domainRows = remoteRows.mapNotNull { it.toDomain() }
        val parseSkipped = remoteRows.size - domainRows.size
        if (parseSkipped > 0) {
            SyncLog.info("$PHASE_HIGHLIGHTS_PULL ignored $parseSkipped row(s) without server id")
        }

        if (!noteEncryptionRepository.requiresCloudRecovery()) {
            bibleHighlightRepository.reconcileWithRemote(domainRows)
        } else {
            SyncLog.skipped(PHASE_HIGHLIGHTS_PULL, "cloud recovery password required — notes not merged")
        }
        SyncLog.ok(PHASE_HIGHLIGHTS_PULL, "reconciled ${domainRows.size} row(s)")
        return HighlightPullResult(
            SyncPhaseOutcome.Ok(PHASE_HIGHLIGHTS_PULL, "${domainRows.size} remote row(s)"),
            remoteNoteByServerId,
        )
    }

    private fun MorningDayRecordRow.toDomain(): MorningDayRecord? {
        val parsedOutcome = runCatching { MorningDayOutcome.valueOf(outcome) }.getOrNull() ?: return null
        val recordedMs = recordedAt?.let { parseInstant(it) } ?: System.currentTimeMillis()
        return MorningDayRecord(
            dateKey = dateKey,
            outcome = parsedOutcome,
            recordedAtEpochMs = recordedMs,
            timeOnGoalSeconds = timeOnGoalSeconds.coerceAtLeast(0),
        )
    }

    private fun BibleHighlightRow.toDomain(): BibleHighlight? {
        val serverId = id ?: return null
        val createdMs = createdAt?.let { parseInstant(it) } ?: System.currentTimeMillis()
        return BibleHighlight(
            id = clientId ?: 0L,
            remoteId = serverId,
            translationId = translationId,
            book = book,
            chapter = chapter,
            label = label,
            excerpt = excerpt,
            verse = verse,
            verseEnd = verseEnd,
            verseNumbers = verseNumbers,
            colorKey = colorKey ?: BibleHighlightColors.DEFAULT_KEY,
            note = note?.orEmpty() ?: "",
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            createdAtEpochMs = createdMs,
        )
    }

    private fun recordSyncFailure(phase: String, error: Throwable) {
        SyncLog.failure(phase, error)
        errorReporter.reportNonFatal(
            throwable = error,
            tags = mapOf(
                "feature" to "supabase_sync",
                "sync.phase" to phase,
            ),
            extras = SyncLog.sentryExtrasFor(error),
        )
    }

    private fun finishSyncRun(label: String, report: UserDataSyncReport) {
        SyncLog.reportCompleted(label, report)
        errorReporter.breadcrumb("supabase_sync", "$label — ${report.summary()}")
    }

    private fun preconditionFailure(cause: SyncPreconditionFailure): Result<UserDataSyncReport> {
        SyncLog.skipped("sync", cause.message ?: cause.javaClass.simpleName)
        return Result.failure(cause)
    }

    private fun UserDataSyncReport.toResult(): Result<UserDataSyncReport> =
        if (hasFailures) {
            Result.failure(UserDataSyncException(this))
        } else {
            Result.success(this)
        }

    companion object {
        private const val TABLE_MORNING_DAYS = "morning_day_records"
        private const val TABLE_HIGHLIGHTS = "bible_highlights"
        private const val TABLE_NOTE_CRYPTO = "user_note_crypto"

        private const val PHASE_NOTE_CRYPTO_PULL = "user_note_crypto pull"
        private const val PHASE_NOTE_CRYPTO_PUSH = "user_note_crypto push"
        private const val PHASE_NOTE_CRYPTO = "user_note_crypto sync"

        private const val PHASE_MORNING_PULL = "morning_day_records pull"
        private const val PHASE_MORNING_PUSH = "morning_day_records push"
        private const val PHASE_HIGHLIGHTS_PULL = "bible_highlights pull"
        private const val PHASE_HIGHLIGHTS_PUSH = "bible_highlights push"
        private const val PHASE_HIGHLIGHTS = "bible_highlights sync"

        private fun instantToIso(epochMs: Long): String =
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))

        private fun parseInstant(value: String): Long =
            runCatching { Instant.parse(value) }.getOrElse {
                runCatching { Instant.parse(value.replace(" ", "T")) }.getOrDefault(Instant.EPOCH)
            }.toEpochMilli()
    }
}
