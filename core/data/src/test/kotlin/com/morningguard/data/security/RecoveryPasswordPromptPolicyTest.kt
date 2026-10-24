package com.morningguard.data.security

import com.morningguard.domain.model.DataStorageMode
import com.morningguard.domain.model.UserSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPasswordPromptPolicyTest {

    @Test
    fun `sheet after sign in when cloud sync without recovery password`() {
        val settings = UserSettings(
            dataStorageMode = DataStorageMode.CLOUD_SYNC,
            recoveryPasswordPromptShownAfterSignIn = false,
        )
        assertTrue(
            RecoveryPasswordPromptPolicy.shouldShowDedicatedSheet(
                settings = settings,
                hasRecoveryPassword = false,
                signedIn = true,
                cloudRecoveryRequired = false,
            ),
        )
    }

    @Test
    fun `no banner when recovery password set`() {
        val settings = UserSettings(dataStorageMode = DataStorageMode.CLOUD_SYNC)
        assertFalse(
            RecoveryPasswordPromptPolicy.shouldShowBanner(
                settings = settings,
                hasRecoveryPassword = true,
                signedIn = true,
            ),
        )
    }
}
