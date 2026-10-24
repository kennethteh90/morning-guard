package com.morningguard.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCryptoInteropVectorsTest {

    @Test
    fun `password wrap unwrap matches shared DEK`() {
        val keyManager = NoteCryptoKeyManager()
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { it.toByte() }
        val wrap = keyManager.wrapDekWithPassword(
            Vectors.DEK_BYTES,
            Vectors.PASSWORD.toCharArray(),
            salt,
            iv,
        )
        assertEquals(Vectors.SALT_B64, wrap.saltBase64)
        assertEquals(Vectors.IV_B64, wrap.ivBase64)
        assertEquals(Vectors.PASSWORD_WRAPPED_DEK_B64, wrap.wrappedDekBase64)
        val dek = keyManager.unwrapDekWithPassword(wrap, Vectors.PASSWORD.toCharArray())
        assertArrayEquals(Vectors.DEK_BYTES, dek)
    }

    @Test
    fun `note ciphertext decrypts with shared DEK`() {
        val plain = NoteContentCipher.decrypt(Vectors.NOTE_CIPHERTEXT, Vectors.DEK_BYTES)
        assertEquals(Vectors.NOTE_PLAINTEXT, plain)
    }

    private object Vectors {
        const val PASSWORD = "TestPass1"
        const val SALT_B64 = "AAECAwQFBgcICQoLDA0ODw=="
        const val IV_B64 = "AAECAwQFBgcICQoL"
        const val PASSWORD_WRAPPED_DEK_B64 =
            "LrvW3v30+5XpBqxJUdMPtU+FPK3/B6Gq1k7sbPd5Ae++QLKzEsHrVxCSXCY3XUK5"
        val DEK_BYTES = ByteArray(32) { it.toByte() }
        const val NOTE_PLAINTEXT = "Morning reflection"
        const val NOTE_CIPHERTEXT =
            "mgenc1:CgsMDQ4PEBESExQVItJIpgKzsFXEYb5hu8+RIZJwzKLDU5+dhZ2gmQHvscoH/w=="
    }
}
