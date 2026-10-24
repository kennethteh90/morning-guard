package com.morningguard.app.bible

import com.morningguard.data.bible.BibleProxyLicensedAuthException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Caches a short-lived licensed session and single-flights minting (challenge + attestation).
 * Retries transient Integrity failures with backoff without parallel attestations.
 */
class LicensedSessionCoordinator(
    private val mintSession: suspend () -> SessionGrant,
    private val isTransientFailure: (Throwable) -> Boolean,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val sleepMs: suspend (Long) -> Unit = { delay(it) },
    private val maxAttempts: Int = 3,
) {
    data class SessionGrant(
        val token: String,
        /** Epoch millis when the token should no longer be used client-side. */
        val expiresAtEpochMs: Long,
    )

    private val mutex = Mutex()
    private val cached = AtomicReference<SessionGrant?>(null)
    private val cooldownUntilElapsedMs = AtomicLong(0L)

    suspend fun sessionToken(): String {
        cached.get()?.takeIf { it.expiresAtEpochMs > nowEpochMs() + EXPIRY_SKEW_MS }?.let {
            return it.token
        }
        return mutex.withLock {
            cached.get()?.takeIf { it.expiresAtEpochMs > nowEpochMs() + EXPIRY_SKEW_MS }?.let {
                return@withLock it.token
            }
            val now = nowEpochMs()
            if (now < cooldownUntilElapsedMs.get()) {
                throw BibleProxyLicensedAuthException()
            }
            val grant = mintWithRetry()
            cached.set(grant)
            grant.token
        }
    }

    fun invalidate() {
        cached.set(null)
    }

    fun cachedTokenForTests(): String? = cached.get()?.token

    private suspend fun mintWithRetry(): SessionGrant {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return mintSession()
            } catch (e: Throwable) {
                lastError = e
                if (!isTransientFailure(e) || attempt == maxAttempts - 1) {
                    cooldownUntilElapsedMs.set(nowEpochMs() + COOLDOWN_AFTER_EXHAUSTED_MS)
                    throw wrap(e)
                }
                sleepMs(backoffMs(attempt))
            }
        }
        throw wrap(lastError ?: IllegalStateException("session mint failed"))
    }

    private fun wrap(error: Throwable): BibleProxyLicensedAuthException =
        error as? BibleProxyLicensedAuthException
            ?: BibleProxyLicensedAuthException(cause = error)

    companion object {
        const val EXPIRY_SKEW_MS = 60_000L
        const val COOLDOWN_AFTER_EXHAUSTED_MS = 60_000L

        fun backoffMs(attemptZeroBased: Int): Long = when (attemptZeroBased) {
            0 -> 1_000L
            1 -> 3_000L
            else -> 6_000L
        }
    }
}
