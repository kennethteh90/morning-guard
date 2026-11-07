package com.morningguard.app.monitoring

import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryReportingPolicyTest {

    @Test
    fun `shouldReport skips cancellation`() {
        assertFalse(SentryReportingPolicy.shouldReport(CancellationException()))
    }

    @Test
    fun `shouldDropEvent drops credential cancellation type`() {
        val event = SentryEvent().apply {
            exceptions = listOf(
                SentryException().apply {
                    type = "androidx.credentials.exceptions.GetCredentialCancellationException"
                    value = "[16] Cancelled by user."
                },
            )
        }
        assertTrue(SentryReportingPolicy.shouldDropEvent(event))
    }

    @Test
    fun `shouldDropEvent keeps real errors`() {
        val event = SentryEvent().apply {
            exceptions = listOf(
                SentryException().apply {
                    type = "java.lang.IllegalStateException"
                    value = "sync failed"
                },
            )
        }
        assertFalse(SentryReportingPolicy.shouldDropEvent(event))
    }
}
