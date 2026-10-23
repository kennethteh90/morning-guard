package com.morningguard.app.sync

/**
 * Outcome of one pull/push step. [Skipped] is an expected no-op (unsigned, nothing local, etc.).
 */
sealed interface SyncPhaseOutcome {
    val phase: String

    data class Ok(
        override val phase: String,
        val detail: String,
    ) : SyncPhaseOutcome

    data class Skipped(
        override val phase: String,
        val reason: String,
    ) : SyncPhaseOutcome

    data class Failed(
        override val phase: String,
        val error: Throwable,
    ) : SyncPhaseOutcome
}

data class UserDataSyncReport(
    val outcomes: List<SyncPhaseOutcome>,
) {
    val hasFailures: Boolean get() = outcomes.any { it is SyncPhaseOutcome.Failed }

    fun summary(): String = outcomes.joinToString("; ") { outcome ->
        when (outcome) {
            is SyncPhaseOutcome.Ok -> "${outcome.phase}: ok (${outcome.detail})"
            is SyncPhaseOutcome.Skipped -> "${outcome.phase}: skipped (${outcome.reason})"
            is SyncPhaseOutcome.Failed -> "${outcome.phase}: failed (${SyncLog.messageFor(outcome.error)})"
        }
    }

    companion object {
        fun single(outcome: SyncPhaseOutcome) = UserDataSyncReport(listOf(outcome))

        fun merge(reports: List<UserDataSyncReport>): UserDataSyncReport =
            UserDataSyncReport(reports.flatMap { it.outcomes })
    }
}

class UserDataSyncException(val report: UserDataSyncReport) :
    Exception(report.summary())

sealed class SyncPreconditionFailure(message: String) : Exception(message) {
    class NotConfigured : SyncPreconditionFailure("Supabase is not configured")
    class NotSignedIn : SyncPreconditionFailure("No active auth session")
}
