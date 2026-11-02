package com.morningguard.app.auth

import io.github.jan.supabase.exceptions.RestException

/**
 * Classifies Supabase Auth REST failures so expected session expiry triggers re-auth
 * instead of Sentry noise.
 */
object SupabaseAuthFailurePolicy {

    fun isAuthRestFailure(error: Throwable): Boolean =
        error.javaClass.simpleName == "AuthRestException"

    fun isStaleSessionFailure(error: Throwable): Boolean {
        val rest = error as? RestException ?: return false
        if (!isAuthRestFailure(error)) {
            return false
        }
        if (rest.statusCode == 401 || rest.statusCode == 403) {
            return true
        }
        val body = rest.error?.lowercase().orEmpty()
        return body.contains("refresh_token") ||
            body.contains("invalid_grant") ||
            body.contains("session_not_found") ||
            body.contains("token is expired") ||
            body.contains("invalid refresh token")
    }

    fun shouldReportToMonitoring(error: Throwable): Boolean = !isStaleSessionFailure(error)

    fun userFacingMessage(error: Throwable): String {
        if (isStaleSessionFailure(error)) {
            return "Your session expired. Sign in again to continue syncing."
        }
        return error.message?.takeIf { it.isNotBlank() }
            ?: "Google sign-in failed"
    }
}
