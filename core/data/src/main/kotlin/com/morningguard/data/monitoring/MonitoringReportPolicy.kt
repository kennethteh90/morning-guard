package com.morningguard.data.monitoring

import kotlinx.coroutines.CancellationException

/**
 * Shared rules for what should reach backend monitoring (Sentry).
 * Keeps normal coroutine cancellation and benign auth/session noise out of issue lists.
 */
object MonitoringReportPolicy {

    fun shouldReport(throwable: Throwable): Boolean {
        if (isCoroutineCancellation(throwable)) {
            return false
        }
        if (isBenignKeystoreIvMisconfiguration(throwable)) {
            return false
        }
        return true
    }

    fun isCoroutineCancellation(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is CancellationException) {
                return true
            }
            val name = current.javaClass.name
            if (name.endsWith("CancellationException")) {
                return true
            }
            val message = current.message.orEmpty()
            if (message.contains("Job was cancelled", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /** Fixed in 1.0.8+; older builds crashed on first Keystore DEK wrap. */
    fun isBenignKeystoreIvMisconfiguration(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is java.security.InvalidAlgorithmParameterException &&
                current.message?.contains("Caller-provided IV not permitted", ignoreCase = true) == true
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
