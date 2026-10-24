package com.morningguard.domain.bible

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BibleProxyUserMessagesTest {
    @Test
    fun rateLimit429MentionsWait() {
        val message = BibleProxyUserMessages.friendlyMessage(429, "Rate limit exceeded")
        assertTrue(message.contains("minute", ignoreCase = true))
    }
}
