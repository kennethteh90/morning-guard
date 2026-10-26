package com.morningguard.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncLogTest {

    @Test
    fun `sentryExtrasFor omits free-form error text`() {
        val extras = SyncLog.sentryExtrasFor(IllegalStateException("secret@example.com"))
        assertEquals("IllegalStateException", extras["error_type"])
        assertFalse(extras.containsKey("detail"))
        assertFalse(extras.values.any { it.contains("@") })
    }

}
