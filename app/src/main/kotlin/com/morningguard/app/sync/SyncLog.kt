package com.morningguard.app.sync

import android.util.Log
import io.github.jan.supabase.exceptions.RestException

internal object SyncLog {
    const val TAG = "UserDataSync"

    fun info(message: String) {
        Log.i(TAG, message)
    }

    fun skipped(phase: String, reason: String) {
        Log.i(TAG, "$phase skipped: $reason")
    }

    fun ok(phase: String, detail: String) {
        Log.i(TAG, "$phase ok: $detail")
    }

    fun failure(phase: String, error: Throwable) {
        Log.w(TAG, "$phase failed: ${messageFor(error)}", error)
    }

    fun reportCompleted(label: String, report: UserDataSyncReport) {
        if (report.hasFailures) {
            Log.w(TAG, "$label finished with errors — ${report.summary()}")
        } else {
            Log.i(TAG, "$label finished — ${report.summary()}")
        }
    }

    fun messageFor(error: Throwable): String {
        if (error is RestException) {
            val body = error.error?.trim().orEmpty()
            return buildString {
                append("HTTP ${error.statusCode}")
                if (body.isNotEmpty()) {
                    append(" — ")
                    append(body.take(500))
                } else if (!error.message.isNullOrBlank()) {
                    append(" — ")
                    append(error.message)
                }
            }
        }
        return error.message ?: error.javaClass.simpleName
    }

    /** Safe metadata for Sentry — no response bodies or free-form error text. */
    fun sentryExtrasFor(error: Throwable): Map<String, String> {
        val extras = mutableMapOf("error_type" to error.javaClass.simpleName)
        if (error is RestException) {
            extras["http_status"] = error.statusCode.toString()
        }
        return extras
    }
}
