package com.morningguard.data.monitoring

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.InvalidAlgorithmParameterException

class MonitoringReportPolicyTest {

    @Test
    fun `skips coroutine cancellation`() {
        assertFalse(MonitoringReportPolicy.shouldReport(CancellationException("Job was cancelled")))
    }

    @Test
    fun `skips job cancelled message on generic exception`() {
        assertFalse(MonitoringReportPolicy.shouldReport(IllegalStateException("Job was cancelled")))
    }

    @Test
    fun `skips keystore caller IV misconfiguration`() {
        val error = InvalidAlgorithmParameterException("Caller-provided IV not permitted")
        assertFalse(MonitoringReportPolicy.shouldReport(error))
    }

    @Test
    fun `reports unrelated errors`() {
        assertTrue(MonitoringReportPolicy.shouldReport(IllegalStateException("sync failed")))
    }
}
