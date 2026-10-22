package com.morningguard.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOwnershipTest {

    @Test
    fun `uploads when bound user matches`() {
        assertTrue(SyncOwnership.shouldUploadLocalHighlights("user-a", "user-a"))
    }

    @Test
    fun `uploads when no bound user`() {
        assertTrue(SyncOwnership.shouldUploadLocalHighlights(null, "user-b"))
    }

    @Test
    fun `does not upload when bound user differs`() {
        assertFalse(SyncOwnership.shouldUploadLocalHighlights("user-a", "user-b"))
    }
}
