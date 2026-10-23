package com.morningguard.domain.security

class EmergencyBypassPolicy(
    private val minLength: Int = 8,
    private val bypassDurationMs: Long = 60 * 60 * 1000L,
    val maxFailedAttempts: Int = MAX_FAILED_ATTEMPTS,
    val lockoutDurationMs: Long = LOCKOUT_DURATION_MS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    fun validateLength(passphrase: String): Boolean = passphrase.length >= minLength

    fun bypassExpiresAt(nowMs: Long = nowProvider()): Long = nowMs + bypassDurationMs

    fun isLocked(lockedUntilEpochMs: Long?, nowMs: Long = nowProvider()): Boolean =
        lockedUntilEpochMs != null && nowMs < lockedUntilEpochMs

    fun effectiveFailedAttempts(
        failedAttempts: Int,
        lockedUntilEpochMs: Long?,
        nowMs: Long = nowProvider(),
    ): Int = if (lockedUntilEpochMs != null && nowMs >= lockedUntilEpochMs) 0 else failedAttempts

    fun recordFailure(
        failedAttempts: Int,
        lockedUntilEpochMs: Long?,
        nowMs: Long = nowProvider(),
    ): Pair<Int, Long?> {
        if (isLocked(lockedUntilEpochMs, nowMs)) {
            return failedAttempts to lockedUntilEpochMs
        }
        val current = effectiveFailedAttempts(failedAttempts, lockedUntilEpochMs, nowMs)
        val nextAttempts = current + 1
        val newLockedUntil = if (nextAttempts >= maxFailedAttempts) {
            nowMs + lockoutDurationMs
        } else {
            null
        }
        return nextAttempts to newLockedUntil
    }

    fun clearedLockoutAfterSuccess(): Pair<Int, Long?> = 0 to null

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L
    }
}
