package com.morningguard.data.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM for highlight note bodies (`mgenc1:` prefix). Shared by local storage and sync.
 */
object NoteContentCipher {
    const val CIPHER_PREFIX = "mgenc1:"
    const val ENCRYPTED_PLACEHOLDER = "🔒 Encrypted note — unlock in Settings"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun isCiphertext(stored: String): Boolean = stored.startsWith(CIPHER_PREFIX)

    fun isLegacyPlaintext(stored: String): Boolean =
        stored.isNotEmpty() && !isCiphertext(stored)

    fun encrypt(plain: String, dek: ByteArray): String {
        val trimmed = plain.trim()
        if (trimmed.isEmpty()) return ""
        require(dek.size == 32) { "DEK must be 32 bytes." }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(trimmed.encodeToByteArray())
        val payload = iv + ciphertext
        return CIPHER_PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    fun decrypt(stored: String, dek: ByteArray): String {
        if (stored.isEmpty()) return ""
        if (!isCiphertext(stored)) return stored
        require(dek.size == 32) { "DEK must be 32 bytes." }
        val payload = Base64.getDecoder().decode(stored.removePrefix(CIPHER_PREFIX))
        if (payload.size <= IV_BYTES) return ENCRYPTED_PLACEHOLDER
        val iv = payload.copyOfRange(0, IV_BYTES)
        val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).decodeToString()
    }
}
