package com.morningguard.app.monitoring

import android.provider.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SentryEnvironmentTest {

    @Test
    fun `detects Firebase test lab flag`() {
        val context = RuntimeEnvironment.getApplication()
        Settings.System.putString(context.contentResolver, "firebase.test.lab", "true")
        assertTrue(SentryEnvironment.isFirebaseTestLab(context))
    }

    @Test
    fun `returns false when test lab flag is not true`() {
        val context = RuntimeEnvironment.getApplication()
        Settings.System.putString(context.contentResolver, "firebase.test.lab", "false")
        assertFalse(SentryEnvironment.isFirebaseTestLab(context))
    }
}
