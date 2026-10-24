package com.morningguard.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class NoteContentCipherTest {

    @Test
    fun `encrypt decrypt round trip`() {
        val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = NoteContentCipher.encrypt("Morning reflection", dek)
        assertTrue(cipher.startsWith(NoteContentCipher.CIPHER_PREFIX))
        assertEquals("Morning reflection", NoteContentCipher.decrypt(cipher, dek))
    }

    @Test
    fun `legacy plaintext detection`() {
        assertTrue(NoteContentCipher.isLegacyPlaintext("hello"))
        assertFalse(NoteContentCipher.isLegacyPlaintext(""))
        assertFalse(NoteContentCipher.isLegacyPlaintext(NoteContentCipher.CIPHER_PREFIX + "abc"))
    }
}
