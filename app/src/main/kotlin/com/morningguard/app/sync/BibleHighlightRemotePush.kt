package com.morningguard.app.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

internal data class HighlightPushStats(
    val succeeded: Int,
    val failed: Int,
    val lastError: Throwable?,
)

internal object BibleHighlightRemotePush {
    private const val TABLE_HIGHLIGHTS = "bible_highlights"

    suspend fun push(
        client: SupabaseClient,
        payloads: List<BibleHighlightPushPayload>,
    ): HighlightPushStats {
        val updates = payloads.filterIsInstance<BibleHighlightPushPayload.UpdateExisting>().map { it.row }
        val inserts = payloads.filterIsInstance<BibleHighlightPushPayload.InsertNew>()
        val updateStats = pushUpdates(client, updates)
        val insertStats = pushInserts(client, inserts)
        return HighlightPushStats(
            succeeded = updateStats.succeeded + insertStats.succeeded,
            failed = updateStats.failed + insertStats.failed,
            lastError = insertStats.lastError ?: updateStats.lastError,
        )
    }

    fun outcomeFromStats(
        stats: HighlightPushStats,
        payloadCount: Int,
        onFailure: (String, Throwable) -> Unit,
    ): SyncPhaseOutcome {
        return when {
            stats.failed > 0 && stats.succeeded == 0 -> {
                val error = stats.lastError ?: IllegalStateException("highlight push failed")
                onFailure(PHASE_HIGHLIGHTS_PUSH, error)
                SyncPhaseOutcome.Failed(PHASE_HIGHLIGHTS_PUSH, error)
            }
            stats.failed > 0 -> {
                SyncLog.info(
                    "$PHASE_HIGHLIGHTS_PUSH partial: ${stats.succeeded} ok, ${stats.failed} failed",
                )
                onFailure(
                    PHASE_HIGHLIGHTS_PUSH,
                    stats.lastError ?: IllegalStateException("partial highlight push failure"),
                )
                SyncPhaseOutcome.Ok(
                    PHASE_HIGHLIGHTS_PUSH,
                    "${stats.succeeded}/$payloadCount local row(s)",
                )
            }
            payloadCount == 0 -> {
                SyncLog.skipped(PHASE_HIGHLIGHTS_PUSH, "nothing to upsert after note policy")
                SyncPhaseOutcome.Skipped(PHASE_HIGHLIGHTS_PUSH, "nothing to upsert after note policy")
            }
            else -> {
                SyncLog.ok(PHASE_HIGHLIGHTS_PUSH, "upserted ${stats.succeeded} row(s)")
                SyncPhaseOutcome.Ok(PHASE_HIGHLIGHTS_PUSH, "${stats.succeeded} local row(s)")
            }
        }
    }

    private suspend fun pushUpdates(
        client: SupabaseClient,
        updates: List<BibleHighlightUpdateRow>,
    ): HighlightPushStats {
        if (updates.isEmpty()) return HighlightPushStats(0, 0, null)
        val batchResult = runCatching {
            client.postgrest.from(TABLE_HIGHLIGHTS).upsert(updates) {
                onConflict = "id"
            }
        }
        if (batchResult.isSuccess) {
            return HighlightPushStats(updates.size, 0, null)
        }
        return pushUpdatesIndividually(client, updates)
    }

    private suspend fun pushUpdatesIndividually(
        client: SupabaseClient,
        updates: List<BibleHighlightUpdateRow>,
    ): HighlightPushStats {
        var succeeded = 0
        var failed = 0
        var lastError: Throwable? = null
        for (row in updates) {
            val rowResult = runCatching {
                client.postgrest.from(TABLE_HIGHLIGHTS).upsert(listOf(row)) {
                    onConflict = "id"
                }
            }
            if (rowResult.isSuccess) {
                succeeded++
            } else {
                failed++
                lastError = rowResult.exceptionOrNull()
                SyncLog.failure(PHASE_HIGHLIGHTS_PUSH, lastError!!)
            }
        }
        return HighlightPushStats(succeeded, failed, lastError)
    }

    private suspend fun pushInserts(
        client: SupabaseClient,
        inserts: List<BibleHighlightPushPayload.InsertNew>,
    ): HighlightPushStats {
        var succeeded = 0
        var failed = 0
        var lastError: Throwable? = null
        for (payload in inserts) {
            val rowResult = runCatching {
                client.postgrest.from(TABLE_HIGHLIGHTS).upsert(listOf(payload.row)) {
                    onConflict = "user_id,client_id"
                }
            }
            if (rowResult.isSuccess) {
                succeeded++
            } else {
                failed++
                lastError = rowResult.exceptionOrNull()
                SyncLog.failure(PHASE_HIGHLIGHTS_PUSH, lastError!!)
            }
        }
        return HighlightPushStats(succeeded, failed, lastError)
    }

    private const val PHASE_HIGHLIGHTS_PUSH = "bible_highlights push"
}
