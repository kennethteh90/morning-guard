package com.morningguard.app.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jan.supabase.auth.user.UserSession
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class KeystoreSessionManagerTest {

    private lateinit var context: Context
    private lateinit var manager: KeystoreSessionManager

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = KeystoreSessionManager(context, KeystoreSessionManager.testCipher())
        File(context.filesDir, KeystoreSessionManager.SESSION_FILE_NAME).delete()
    }

    @Test
    fun saveSessionThenLoadSessionRoundTrips() = runTest {
        val session = sampleSession()

        manager.saveSession(session)
        val loaded = manager.loadSession()

        assertNotNull(loaded)
        assertEquals(session.accessToken, loaded?.accessToken)
        assertEquals(session.refreshToken, loaded?.refreshToken)
        assertEquals(session.user?.id, loaded?.user?.id)
    }

    @Test
    fun sessionFileDoesNotContainRefreshTokenPlaintext() = runTest {
        val refreshToken = "super-secret-refresh-token-value"
        val session = sampleSession(refreshToken = refreshToken)

        manager.saveSession(session)

        val sessionFile = File(context.filesDir, KeystoreSessionManager.SESSION_FILE_NAME)
        val onDisk = sessionFile.readBytes().toString(Charsets.ISO_8859_1)

        assertFalse(onDisk.contains("refresh_token"))
        assertFalse(onDisk.contains(refreshToken))
    }

    private fun sampleSession(refreshToken: String = "refresh-token-abc"): UserSession {
        val sessionJson = """
            {
              "access_token": "access-token-xyz",
              "refresh_token": "$refreshToken",
              "expires_in": 3600,
              "token_type": "bearer",
              "user": {
                "id": "user-123",
                "aud": "authenticated"
              }
            }
        """.trimIndent()
        return json.decodeFromString(UserSession.serializer(), sessionJson)
    }
}
