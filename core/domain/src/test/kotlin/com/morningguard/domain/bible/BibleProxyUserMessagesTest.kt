package com.morningguard.domain.bible

import org.junit.Assert.assertTrue
import org.junit.Test

class BibleProxyUserMessagesTest {
    @Test
    fun rateLimit429MentionsWait() {
        val message = BibleProxyUserMessages.friendlyMessage(429, "Rate limit exceeded")
        assertTrue(message.contains("minute", ignoreCase = true))
    }
}
