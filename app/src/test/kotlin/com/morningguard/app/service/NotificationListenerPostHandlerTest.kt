package com.morningguard.app.service

import com.morningguard.domain.gate.GateEvaluation
import com.morningguard.domain.gate.GateState
import com.morningguard.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationListenerPostHandlerTest {

    private val settings = UserSettings()
    private val peaceLocked = GateEvaluation(GateState.LOCKED, "2026-11-13", "peace_session")
    private val inactive = GateEvaluation(GateState.INACTIVE, "2026-11-13", "outside_window")

    @Test
    fun `whatsapp intercepted during peace session`() {
        val action = NotificationListenerPostHandler.evaluatePost(
            packageName = "com.whatsapp",
            category = null,
            settings = settings,
            evaluation = peaceLocked,
            appPackageName = "com.morningguard.app",
        )
        assertTrue(action.shouldIntercept)
    }

    @Test
    fun `whatsapp skipped when gate inactive and digest only during gate`() {
        val action = NotificationListenerPostHandler.evaluatePost(
            packageName = "com.whatsapp",
            category = null,
            settings = settings,
            evaluation = inactive,
            appPackageName = "com.morningguard.app",
        )
        assertFalse(action.shouldIntercept)
        assertTrue(action.skipReason!!.contains("gate_inactive"))
    }

    @Test
    fun `cancel only after successful capture`() {
        assertFalse(NotificationListenerPostHandler.shouldCancelAfterCapture(null))
        assertTrue(NotificationListenerPostHandler.shouldCancelAfterCapture(42L))
    }
}
