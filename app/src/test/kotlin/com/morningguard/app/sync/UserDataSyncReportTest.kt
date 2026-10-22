package com.morningguard.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataSyncReportTest {
    @Test
    fun hasFailures_whenAnyPhaseFailed() {
        val report = UserDataSyncReport(
            listOf(
                SyncPhaseOutcome.Ok("pull", "1 row"),
                SyncPhaseOutcome.Failed("push", IllegalStateException("boom")),
            ),
        )
        assertTrue(report.hasFailures)
        assertTrue(report.summary().contains("push: failed"))
    }

    @Test
    fun success_whenOnlyOkAndSkipped() {
        val report = UserDataSyncReport(
            listOf(
                SyncPhaseOutcome.Ok("pull", "0 rows"),
                SyncPhaseOutcome.Skipped("push", "no local history"),
            ),
        )
        assertFalse(report.hasFailures)
    }
}
