package com.morningguard.app.bible

import com.morningguard.data.bible.BibleProxyLicensedAuthException
import com.morningguard.data.monitoring.ErrorReporter
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side cooldown and throttled Sentry reporting for licensed Bible proxy auth.
 */
internal class LicensedAuthFailureGate(
    private val errorReporter: ErrorReporter,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val cooldownMs: Long = LicensedSessionCoordinator.COOLDOWN_AFTER_EXHAUSTED_MS,
) {
    private val cooldownUntilMs = AtomicLong(0L)
    private val lastSentryReportMs = AtomicLong(0L)

    fun inCooldown(): Boolean = nowEpochMs() < cooldownUntilMs.get()

    fun markFailure() {
        cooldownUntilMs.set(nowEpochMs() + cooldownMs)
    }

    fun reportFailure(error: Throwable) {
        val now = nowEpochMs()
        if (now - lastSentryReportMs.get() < cooldownMs) {
            errorReporter.breadcrumb(
                "bible_licensed_auth",
                error.message ?: BibleProxyLicensedAuthException.USER_FACING_MESSAGE,
            )
            return
        }
        lastSentryReportMs.set(now)
        errorReporter.reportNonFatal(
            error,
            tags = mapOf("area" to "bible_licensed_auth"),
            extras = mapOf("cause" to (error.cause?.javaClass?.simpleName ?: "none")),
        )
    }
}
