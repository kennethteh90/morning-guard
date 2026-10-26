package com.morningguard.app.monitoring

import io.sentry.SentryEvent
import io.sentry.protocol.Message

private val EMAIL_LIKE = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)

object SentryScrubber {

    fun redact(text: String): String = text.replace(EMAIL_LIKE, "[redacted-email]")

    fun scrubEvent(event: SentryEvent): SentryEvent {
        event.message = event.message?.let { scrubMessage(it) }
        event.request?.headers?.remove("Authorization")
        event.request?.headers?.remove("apikey")
        return event
    }

    private fun scrubMessage(message: Message): Message {
        message.message = message.message?.let { redact(it) }
        message.formatted = message.formatted?.let { redact(it) }
        return message
    }
}
