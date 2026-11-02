package com.morningguard.app.monitoring

import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryScrubberTest {

    @Test
    fun `redact removes bearer tokens and apikey headers from supabase error text`() {
        val raw =
            """
            BadRequestRestException: HTTP 400
            Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNrLHC501OVXmmqKIMmWPGmMENGgJ
            apikey: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiJ9.secret
            user@example.com
            """.trimIndent()

        val scrubbed = SentryScrubber.redact(raw)
        assertFalse(scrubbed.contains("Bearer eyJ"))
        assertFalse(scrubbed.contains("apikey: eyJ"))
        assertFalse(scrubbed.contains("user@example.com"))
        assertTrue(scrubbed.contains("Authorization: [redacted]"))
        assertTrue(scrubbed.contains("apikey: [redacted]"))
        assertTrue(scrubbed.contains("[redacted-email]"))
    }

    @Test
    fun `scrubEvent redacts exception values and strips request auth headers`() {
        val event = io.sentry.SentryEvent().apply {
            message = Message().apply { message = "Authorization: Bearer secret-token" }
            exceptions = listOf(
                SentryException().apply {
                    value = "apikey: leaked-key"
                },
            )
            request = io.sentry.protocol.Request().apply {
                headers = mutableMapOf(
                    "Authorization" to "Bearer secret-token",
                    "apikey" to "anon-key",
                    "Content-Type" to "application/json",
                )
            }
        }

        val scrubbed = SentryScrubber.scrubEvent(event)
        assertFalse(scrubbed!!.exceptions!!.first().value!!.contains("leaked-key"))
        assertFalse(scrubbed.request!!.headers!!.containsKey("Authorization"))
        assertFalse(scrubbed.request!!.headers!!.containsKey("apikey"))
        assertTrue(scrubbed!!.request!!.headers!!["Content-Type"] == "application/json")
    }

    @Test
    fun `scrubEvent drops benign cancellation events`() {
        val event = io.sentry.SentryEvent().apply {
            exceptions = listOf(
                SentryException().apply {
                    type = "kotlinx.coroutines.JobCancellationException"
                    value = "Job was cancelled"
                },
            )
        }
        assertTrue(SentryScrubber.scrubEvent(event) == null)
    }
}
