package com.morningguard.app

import org.junit.Assert.assertNotEquals
import org.junit.Test

class OnboardingDisclosureStringsTest {

    @Test
    fun `notification disclosure string resource is defined`() {
        assertNotEquals(0, R.string.notification_access_disclosure)
        assertNotEquals(0, R.string.notification_access_disclosure_checkbox)
    }
}
