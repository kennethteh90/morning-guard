package com.morningguard.domain.notification

import com.morningguard.domain.model.CapturedNotification
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaptureQualityPolicyTest {

    @Test
    fun `skips empty google quick search box`() {
        assertFalse(
            CaptureQualityPolicy.shouldPersist(
                "com.google.android.googlequicksearchbox",
                "",
                "",
            ),
        )
    }

    @Test
    fun `skips generic new message without body`() {
        assertFalse(
            CaptureQualityPolicy.shouldPersist(
                "com.microsoft.office.outlook",
                "New message",
                "",
            ),
        )
    }

    @Test
    fun `keeps outlook with real subject and preview`() {
        assertTrue(
            CaptureQualityPolicy.shouldPersist(
                "com.microsoft.office.outlook",
                "Project update",
                "See attached notes…",
            ),
        )
    }

    @Test
    fun `detects duplicate digest row`() {
        val existing = CapturedNotification(
            id = 1,
            packageName = "com.microsoft.office.outlook",
            title = "Hello",
            text = "Body",
            postedAtEpochMs = 1L,
        )
        val candidate = existing.copy(id = 2, postedAtEpochMs = 2L)
        assertTrue(CaptureQualityPolicy.isDuplicate(existing, candidate))
    }

    @Test
    fun `near duplicate when preview text overlaps`() {
        val existing = CapturedNotification(
            id = 1,
            packageName = "com.slack",
            title = "Team chat",
            text = "Alice: please review the doc before standup today",
            postedAtEpochMs = 1L,
        )
        val candidate = existing.copy(
            id = 2,
            text = "please review the doc before standup",
            postedAtEpochMs = 2L,
        )
        assertTrue(CaptureQualityPolicy.isNearDuplicate(existing, candidate))
    }

    @Test
    fun `outlook same subject different preview is duplicate`() {
        val existing = CapturedNotification(
            id = 1,
            packageName = "com.microsoft.office.outlook",
            title = "Project update",
            text = "Short preview",
            postedAtEpochMs = 1L,
        )
        val candidate = existing.copy(id = 2, text = "Longer preview text from Outlook", postedAtEpochMs = 2L)
        assertTrue(CaptureQualityPolicy.isDuplicate(existing, candidate))
    }
}
