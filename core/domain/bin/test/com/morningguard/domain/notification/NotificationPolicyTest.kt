package com.morningguard.domain.notification

import com.morningguard.domain.gate.GateState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationPolicyTest {

    @Test
    fun `skips alarms and bypass apps`() {
        assertFalse(
            NotificationPolicy.shouldIntercept(
                packageName = "com.example",
                category = NotificationPolicy.ALARM_CATEGORY,
                bypassPackages = emptySet(),
                gateState = GateState.LOCKED,
                digestOnlyDuringMorningGate = true,
                appPackageName = "com.morningguard.app",
            ),
        )
        assertFalse(
            NotificationPolicy.shouldIntercept(
                packageName = "com.bypass",
                category = null,
                bypassPackages = setOf("com.bypass"),
                gateState = GateState.LOCKED,
                digestOnlyDuringMorningGate = true,
                appPackageName = "com.morningguard.app",
            ),
        )
    }

    @Test
    fun `intercepts when gate locked`() {
        assertTrue(
            NotificationPolicy.shouldIntercept(
                packageName = "com.social",
                category = null,
                bypassPackages = emptySet(),
                gateState = GateState.LOCKED,
                digestOnlyDuringMorningGate = true,
                appPackageName = "com.morningguard.app",
            ),
        )
        assertTrue(
            NotificationPolicy.shouldIntercept(
                packageName = "com.whatsapp",
                category = null,
                bypassPackages = emptySet(),
                gateState = GateState.LOCKED,
                digestOnlyDuringMorningGate = true,
                appPackageName = "com.morningguard.app",
            ),
        )
        assertFalse(
            NotificationPolicy.shouldIntercept(
                packageName = "com.social",
                category = null,
                bypassPackages = emptySet(),
                gateState = GateState.INACTIVE,
                digestOnlyDuringMorningGate = true,
                appPackageName = "com.morningguard.app",
            ),
        )
    }

    @Test
    fun `never intercepts calls or dialer packages when locked`() {
        assertFalse(
            NotificationPolicy.shouldIntercept(
                packageName = "com.social",
                category = NotificationPolicy.CALL_CATEGORY,
                bypassPackages = emptySet(),
                gateState = GateState.LOCKED,
                digestOnlyDuringMorningGate = true,
                appPackageName = "com.morningguard.app",
            ),
        )
        assertFalse(
            NotificationPolicy.shouldIntercept(
                packageName = "com.google.android.dialer",
                category = null,
                bypassPackages = emptySet(),
                gateState = GateState.LOCKED,
                digestOnlyDuringMorningGate = true,
                appPackageName = "com.morningguard.app",
            ),
        )
    }

    @Test
    fun `never intercepts default SMS packages when locked`() {
        assertFalse(
            NotificationPolicy.shouldIntercept(
                packageName = "com.google.android.apps.messaging",
                category = null,
                bypassPackages = emptySet(),
                gateState = GateState.LOCKED,
                digestOnlyDuringMorningGate = true,
                appPackageName = "com.morningguard.app",
            ),
        )
        assertFalse(
            NotificationPolicy.shouldIntercept(
                packageName = "com.samsung.android.messaging",
                category = null,
                bypassPackages = emptySet(),
                gateState = GateState.LOCKED,
                digestOnlyDuringMorningGate = true,
                appPackageName = "com.morningguard.app",
            ),
        )
    }
}
