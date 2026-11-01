package com.morningguard.app.monitoring

import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException

private val EMAIL_LIKE = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
private val BEARER_TOKEN = Regex("""Bearer\s+\S+""", RegexOption.IGNORE_CASE)
private val JWT = Regex("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
private val AUTHORIZATION_HEADER = Regex("""Authorization:\s*\S+""", RegexOption.IGNORE_CASE)
private val APIKEY_HEADER = Regex("""apikey:\s*\S+""", RegexOption.IGNORE_CASE)
private val APIKEY_JSON = Regex(""""apikey"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE)

object SentryScrubber {

    fun redact(text: String): String =
        text
            .replace(BEARER_TOKEN, "Bearer [redacted]")
            .replace(JWT, "[redacted-jwt]")
            .replace(AUTHORIZATION_HEADER, "Authorization: [redacted]")
            .replace(APIKEY_HEADER, "apikey: [redacted]")
            .replace(APIKEY_JSON, """"apikey":"[redacted]"""")
            .replace(EMAIL_LIKE, "[redacted-email]")

    fun scrubEvent(event: SentryEvent): SentryEvent {
        event.message = event.message?.let { scrubMessage(it) }
        event.exceptions = event.exceptions?.map { scrubException(it) }
        event.request?.headers?.remove("Authorization")
        event.request?.headers?.remove("apikey")
        event.extras?.replaceAll { _, value ->
            when (value) {
                is String -> redact(value)
                else -> value
            }
        }
        event.breadcrumbs?.forEach { crumb ->
            crumb.message = crumb.message?.let { redact(it) }
            crumb.data?.replaceAll { _, value ->
                when (value) {
                    is String -> redact(value)
                    else -> value
                }
            }
        }
        return event
    }

    private fun scrubMessage(message: Message): Message {
        message.message = message.message?.let { redact(it) }
        message.formatted = message.formatted?.let { redact(it) }
        return message
    }

    private fun scrubException(exception: SentryException): SentryException {
        exception.value = exception.value?.let { redact(it) }
        return exception
    }
}
