package com.morningguard.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Documents the AES-GCM encrypt pattern required for Android Keystore keys (no caller IV on encrypt).
 */
class KeystoreGcmWrapPatternTest {

    @Test
    fun `encrypt without caller IV round trips like NoteCryptoKeyManager keystore wrap`() {
        val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = encryptCipher.iv
        assertEquals(12, iv.size)
        val ciphertext = encryptCipher.doFinal(dek)

        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        assertArrayEquals(dek, decryptCipher.doFinal(ciphertext))
    }
}
