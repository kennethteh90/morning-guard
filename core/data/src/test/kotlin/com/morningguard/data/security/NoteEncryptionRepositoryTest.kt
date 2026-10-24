package com.morningguard.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoteEncryptionRepositoryTest {

    private lateinit var repository: NoteEncryptionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = NoteCryptoStore(context)
        repository = NoteEncryptionRepository(store, NoteCryptoKeyManager())
    }

    @Test
    fun `ensureDekReady encrypts notes without recovery password`() = runTest {
        repository.ensureDekReady().getOrThrow()
        val stored = repository.encryptForStorage("hello")
        assertTrue(stored.startsWith(NoteContentCipher.CIPHER_PREFIX))
    }

    @Test
    fun `setRecoveryPassword wraps existing dek`() = runTest {
        repository.ensureDekReady().getOrThrow()
        val before = repository.encryptForStorage("same text")
        repository.setRecoveryPassword("password123".toCharArray()).getOrThrow()
        repository.lock()
        repository.unlockWithPassword("password123".toCharArray()).getOrThrow()
        val after = repository.decryptFromStorage(before)
        assertEqualsPlaintext("same text", after)
    }

    private fun assertEqualsPlaintext(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
