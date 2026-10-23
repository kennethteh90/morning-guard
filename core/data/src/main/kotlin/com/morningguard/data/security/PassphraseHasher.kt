package com.morningguard.data.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassphraseHasher @Inject constructor() {
    private val random = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(passphrase: String): String {
        val salt = generateSalt()
        val derived = deriveKey(passphrase, salt, ITERATIONS)
        return "$STORAGE_PREFIX$$ITERATIONS$$salt$$derived"
    }

    fun verify(passphrase: String, stored: String): Boolean {
        if (!isModernFormat(stored)) return false
        val parts = stored.split('$')
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = parts[2]
        val expectedHash = parts[3]
        val actualHash = deriveKey(passphrase, salt, iterations)
        return constantTimeEquals(actualHash, expectedHash)
    }

    fun isModernFormat(stored: String?): Boolean =
        stored != null && stored.startsWith("$STORAGE_PREFIX$")

    private fun deriveKey(passphrase: String, saltHex: String, iterations: Int): String {
        val saltBytes = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(passphrase.toCharArray(), saltBytes, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    companion object {
        const val STORAGE_PREFIX = "pbkdf2-sha256"
        const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
    }
}
