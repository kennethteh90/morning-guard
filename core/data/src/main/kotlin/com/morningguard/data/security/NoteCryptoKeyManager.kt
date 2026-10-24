package com.morningguard.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteCryptoKeyManager @Inject constructor() {

    fun generateDek(): ByteArray = ByteArray(DEK_BYTES).also { SecureRandom().nextBytes(it) }

    fun wrapDekWithPassword(dek: ByteArray, password: CharArray): PasswordWrap {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        return wrapDekWithPassword(dek, password, salt, iv)
    }

    /** Fixed salt/IV for cross-platform test vectors (iOS MorningGuardCore interop). */
    fun wrapDekWithPassword(
        dek: ByteArray,
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
    ): PasswordWrap {
        val key = derivePasswordKey(password, salt)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(dek)
        return PasswordWrap(
            wrappedDekBase64 = Base64.getEncoder().encodeToString(ciphertext),
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
        )
    }

    fun unwrapDekWithPassword(wrap: PasswordWrap, password: CharArray): ByteArray {
        val salt = Base64.getDecoder().decode(wrap.saltBase64)
        val iv = Base64.getDecoder().decode(wrap.ivBase64)
        val ciphertext = Base64.getDecoder().decode(wrap.wrappedDekBase64)
        val key = derivePasswordKey(password, salt)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun wrapDekWithKeystore(dek: ByteArray): KeystoreWrap {
        val cipher = Cipher.getInstance(AES_GCM)
        // Keystore AES-GCM keys require a random IV from the provider on encrypt (no caller IV).
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "Unexpected Keystore GCM IV length: ${iv.size}" }
        val ciphertext = cipher.doFinal(dek)
        return KeystoreWrap(
            wrappedDekBase64 = Base64.getEncoder().encodeToString(ciphertext),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
        )
    }

    fun unwrapDekWithKeystore(wrap: KeystoreWrap): ByteArray {
        val iv = Base64.getDecoder().decode(wrap.ivBase64)
        val ciphertext = Base64.getDecoder().decode(wrap.wrappedDekBase64)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        if (!ks.containsAlias(KEY_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
                init(spec)
            }.generateKey()
        }
        return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun derivePasswordKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    data class PasswordWrap(
        val wrappedDekBase64: String,
        val saltBase64: String,
        val ivBase64: String,
    )

    data class KeystoreWrap(
        val wrappedDekBase64: String,
        val ivBase64: String,
    )

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "morning_guard_note_dek_wrap"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val DEK_BYTES = 32
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        const val PBKDF2_ITERATIONS = 120_000
    }
}
