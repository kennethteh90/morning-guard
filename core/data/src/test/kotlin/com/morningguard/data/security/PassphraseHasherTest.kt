package com.morningguard.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseHasherTest {

    private val hasher = PassphraseHasher()

    @Test
    fun `same passphrase verifies against stored hash`() {
        val passphrase = "correct-horse-battery"
        val stored = hasher.hash(passphrase)
        assertTrue(hasher.verify(passphrase, stored))
    }

    @Test
    fun `wrong passphrase does not verify`() {
        val stored = hasher.hash("correct-horse-battery")
        assertFalse(hasher.verify("wrong-passphrase", stored))
    }
}
