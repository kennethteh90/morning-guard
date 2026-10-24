package com.morningguard.data.security

/**
 * Encrypts highlight note text at rest (Room) and for Supabase (ciphertext on wire).
 * Callers must [com.morningguard.data.security.NoteEncryptionRepository.ensureDekReady] before writes.
 */
interface HighlightNoteCipher {
    fun encryptForStorage(plain: String): String
    fun decryptFromStorage(stored: String): String
    /** True when the DEK is loaded and notes can be encrypted or decrypted. */
    fun isEncryptionEnabled(): Boolean
}
