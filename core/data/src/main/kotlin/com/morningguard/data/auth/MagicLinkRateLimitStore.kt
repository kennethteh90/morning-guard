package com.morningguard.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side cooldown so we do not hammer Supabase Auth (especially on the default SMTP tier).
 */
@Singleton
class MagicLinkRateLimitStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun secondsUntilResend(email: String, cooldownMs: Long = DEFAULT_COOLDOWN_MS): Int {
        val lastAt = prefs.getLong(keyLastSentAt(email), 0L)
        if (lastAt == 0L) return 0
        val remainingMs = lastAt + cooldownMs - System.currentTimeMillis()
        return if (remainingMs <= 0L) 0 else ((remainingMs + 999) / 1000).toInt()
    }

    fun canSend(email: String, cooldownMs: Long = DEFAULT_COOLDOWN_MS): Boolean =
        secondsUntilResend(email, cooldownMs) == 0

    fun recordSent(email: String) {
        prefs.edit()
            .putLong(keyLastSentAt(email), System.currentTimeMillis())
            .apply()
    }

    private fun keyLastSentAt(email: String): String =
        "last_sent_${email.trim().lowercase()}"

    companion object {
        private const val PREFS_NAME = "magic_link_rate_limit"
        const val DEFAULT_COOLDOWN_MS = 60_000L
    }
}

class MagicLinkRateLimitedException(
    val retryAfterSeconds: Int,
) : Exception("Wait $retryAfterSeconds seconds before requesting another link.")
