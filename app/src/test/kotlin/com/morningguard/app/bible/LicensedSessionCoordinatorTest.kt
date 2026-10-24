package com.morningguard.app.bible

import com.morningguard.data.bible.BibleProxyLicensedAuthException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LicensedSessionCoordinatorTest {

    @Test
    fun `caches session across calls until invalidate`() = runBlocking {
        val mints = AtomicInteger(0)
        val coordinator = LicensedSessionCoordinator(
            mintSession = {
                mints.incrementAndGet()
                LicensedSessionCoordinator.SessionGrant(
                    token = "session-${mints.get()}",
                    expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L,
                )
            },
            isTransientFailure = { false },
            sleepMs = {},
        )

        assertEquals("session-1", coordinator.sessionToken())
        assertEquals("session-1", coordinator.sessionToken())
        assertEquals(1, mints.get())

        coordinator.invalidate()
        assertEquals("session-2", coordinator.sessionToken())
        assertEquals(2, mints.get())
    }

    @Test
    fun `retries transient failures then succeeds`() = runBlocking {
        val attempts = AtomicInteger(0)
        val coordinator = LicensedSessionCoordinator(
            mintSession = {
                val n = attempts.incrementAndGet()
                if (n < 3) throw FakeTransient("boom-$n")
                LicensedSessionCoordinator.SessionGrant(
                    token = "ok",
                    expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L,
                )
            },
            isTransientFailure = { it is FakeTransient },
            sleepMs = {},
        )

        assertEquals("ok", coordinator.sessionToken())
        assertEquals(3, attempts.get())
    }

    @Test
    fun `cooldown blocks mint after non-transient failure`() = runBlocking {
        val mints = AtomicInteger(0)
        var now = 0L
        val coordinator = LicensedSessionCoordinator(
            mintSession = {
                mints.incrementAndGet()
                throw BibleProxyLicensedAuthException("Licensed Bible session failed (401)")
            },
            isTransientFailure = { false },
            nowEpochMs = { now },
            sleepMs = {},
            maxAttempts = 1,
        )
        try {
            coordinator.sessionToken()
            fail("expected failure")
        } catch (_: BibleProxyLicensedAuthException) {
        }
        assertEquals(1, mints.get())
        now = 1L
        try {
            coordinator.sessionToken()
            fail("expected cooldown failure")
        } catch (_: BibleProxyLicensedAuthException) {
        }
        assertEquals(1, mints.get())
    }

    @Test
    fun `maps exhausted transient failure to licensed auth IOException`() = runBlocking {
        val coordinator = LicensedSessionCoordinator(
            mintSession = { throw FakeTransient("throttled") },
            isTransientFailure = { it is FakeTransient },
            sleepMs = {},
            maxAttempts = 2,
        )
        try {
            coordinator.sessionToken()
            fail("expected failure")
        } catch (e: BibleProxyLicensedAuthException) {
            assertTrue(e.message!!.contains("licensed Bible translation"))
            assertTrue(e.cause is FakeTransient)
        }
    }

    private class FakeTransient(message: String) : RuntimeException(message)
}
