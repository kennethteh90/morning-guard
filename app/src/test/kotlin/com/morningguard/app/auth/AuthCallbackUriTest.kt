package com.morningguard.app.auth

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthCallbackUriTest {

    @Test
    fun `accepts exact auth host with code`() {
        assertTrue(AuthCallbackUri.isAuthCallback(Uri.parse("morningguard://auth?code=abc")))
    }

    @Test
    fun `rejects auth evil host`() {
        assertFalse(AuthCallbackUri.isAuthCallback(Uri.parse("morningguard://auth.evil?code=abc")))
    }

    @Test
    fun `rejects missing uri`() {
        assertFalse(AuthCallbackUri.isAuthCallback(null))
    }

    @Test
    fun `rejects authentication host`() {
        assertFalse(AuthCallbackUri.isAuthCallback(Uri.parse("morningguard://authentication")))
    }

    @Test
    fun `accepts https landing with pkce code`() {
        assertTrue(
            AuthCallbackUri.isAuthCallback(
                Uri.parse("https://morning-guard.codingagents.cc/?code=abc"),
            ),
        )
    }

    @Test
    fun `rejects bare auth deeplink without code`() {
        assertFalse(AuthCallbackUri.isAuthCallback(Uri.parse("morningguard://auth")))
    }

    @Test
    fun `rejects fragment access token on custom scheme`() {
        assertFalse(
            AuthCallbackUri.isAuthCallback(Uri.parse("morningguard://auth#access_token=secret")),
        )
    }

    @Test
    fun `rejects query access token on custom scheme`() {
        assertFalse(
            AuthCallbackUri.isAuthCallback(Uri.parse("morningguard://auth?access_token=secret")),
        )
    }

    @Test
    fun `rejects https landing with fragment access token only`() {
        assertFalse(
            AuthCallbackUri.isAuthCallback(
                Uri.parse("https://morning-guard.codingagents.cc/#access_token=secret"),
            ),
        )
    }
}
