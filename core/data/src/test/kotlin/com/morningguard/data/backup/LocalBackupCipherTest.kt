package com.morningguard.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalBackupCipherTest {

    @Test
    fun `encrypt decrypt round trip`() {
        val password = "backup-pass-phrase".toCharArray()
        val plaintext = """{"schemaVersion":2,"exportedAtEpochMs":1}""".encodeToByteArray()
        val envelope = LocalBackupCipher.encrypt(plaintext, password)
        val decrypted = LocalBackupCipher.decrypt(envelope, password)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `wrong password fails decrypt`() {
        val envelope = LocalBackupCipher.encrypt(byteArrayOf(1, 2, 3), "correct-password".toCharArray())
        try {
            LocalBackupCipher.decrypt(envelope, "wrong-password".toCharArray())
            error("expected decrypt to fail")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test
    fun `ciphertext changes with salt`() {
        val password = "same-password-12".toCharArray()
        val plain = byteArrayOf(9)
        val a = LocalBackupCipher.encrypt(plain, password)
        val b = LocalBackupCipher.encrypt(plain, password)
        assertNotEquals(a.ciphertextBase64, b.ciphertextBase64)
    }
}
