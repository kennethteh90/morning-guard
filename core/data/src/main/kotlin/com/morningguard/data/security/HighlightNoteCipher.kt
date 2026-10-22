package com.morningguard.data.security

/**
 * Encrypts highlight note text at rest (Room) and for Supabase (ciphertext on wire).
 * Plaintext notes without the `mgenc1:` prefix pass through when encryption is off.
 */
interface HighlightNoteCipher {
    fun encryptForStorage(plain: String): String
    fun decryptFromStorage(stored: String): String
    fun isEncryptionEnabled(): Boolean
}
