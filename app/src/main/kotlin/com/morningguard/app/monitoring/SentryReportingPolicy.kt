package com.morningguard.app.monitoring

import androidx.credentials.exceptions.GetCredentialCancellationException
import com.morningguard.data.monitoring.MonitoringReportPolicy
import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import kotlinx.coroutines.CancellationException

object SentryReportingPolicy {

    fun shouldReport(throwable: Throwable): Boolean {
        if (!MonitoringReportPolicy.shouldReport(throwable)) {
            return false
        }
        if (throwable is GetCredentialCancellationException) {
            return false
        }
        if (isCredentialCancellationByName(throwable)) {
            return false
        }
        return true
    }

    fun shouldDropEvent(event: SentryEvent): Boolean {
        val exceptions = event.exceptions.orEmpty()
        if (exceptions.isEmpty()) {
            return false
        }
        return exceptions.all { isBenignExceptionEntry(it) }
    }

    private fun isBenignExceptionEntry(entry: SentryException): Boolean {
        val type = entry.type.orEmpty()
        if (type.endsWith("CancellationException") ||
            type.endsWith("GetCredentialCancellationException")
        ) {
            return true
        }
        val value = entry.value.orEmpty()
        if (value.contains("Job was cancelled", ignoreCase = true) ||
            value.contains("Cancelled by user", ignoreCase = true) ||
            value.contains("activity is cancelled by the user", ignoreCase = true)
        ) {
            return true
        }
        if (value.contains("Caller-provided IV not permitted", ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun isCredentialCancellationByName(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is GetCredentialCancellationException) {
                return true
            }
            if (current.javaClass.name.endsWith("GetCredentialCancellationException")) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /** Types registered with Sentry so uncaught handler drops them before upload. */
    fun ignoredExceptionTypesForInit(): Array<Class<out Throwable>> =
        arrayOf(
            CancellationException::class.java,
            GetCredentialCancellationException::class.java,
        )
}
