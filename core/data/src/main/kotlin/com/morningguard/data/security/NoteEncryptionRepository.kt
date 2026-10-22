package com.morningguard.data.security

import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteEncryptionRepository @Inject constructor(
    private val store: NoteCryptoStore,
    private val keyManager: NoteCryptoKeyManager,
) : HighlightNoteCipher {

    private val mutex = Mutex()
    @Volatile
    private var dek: ByteArray? = null

    val cryptoState: Flow<NoteCryptoState> = store.state

    override fun isEncryptionEnabled(): Boolean = dek != null

    suspend fun state(): NoteCryptoState = store.current()

    suspend fun tryUnlockFromKeystore(): Boolean = mutex.withLock {
        val saved = store.current()
        if (!saved.enabled) return false
        val wrapped = saved.keystoreWrappedDekBase64 ?: return false
        val iv = saved.keystoreWrapIvBase64 ?: return false
        runCatching {
            val bytes = keyManager.unwrapDekWithKeystore(
                NoteCryptoKeyManager.KeystoreWrap(wrapped, iv),
            )
            dek = bytes
            true
        }.getOrDefault(false)
    }

    suspend fun setupWithPassword(password: CharArray): Result<Unit> {
        require(password.size >= 8) { "Password must be at least 8 characters." }
        return runCatching {
            mutex.withLock {
                val dekBytes = keyManager.generateDek()
                val passwordWrap = keyManager.wrapDekWithPassword(dekBytes, password)
                val keystoreWrap = keyManager.wrapDekWithKeystore(dekBytes)
                store.update {
                    NoteCryptoState(
                        enabled = true,
                        passwordWrappedDekBase64 = passwordWrap.wrappedDekBase64,
                        passwordWrapSaltBase64 = passwordWrap.saltBase64,
                        passwordWrapIvBase64 = passwordWrap.ivBase64,
                        keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64,
                        keystoreWrapIvBase64 = keystoreWrap.ivBase64,
                    )
                }
                dek = dekBytes
            }
        }
    }

    suspend fun unlockWithPassword(password: CharArray): Result<Unit> = runCatching {
        mutex.withLock {
            val saved = store.current()
            val wrapped = saved.passwordWrappedDekBase64
                ?: error("Notes encryption is not set up on this device.")
            val salt = saved.passwordWrapSaltBase64 ?: error("Missing encryption salt.")
            val iv = saved.passwordWrapIvBase64 ?: error("Missing encryption metadata.")
            val dekBytes = keyManager.unwrapDekWithPassword(
                NoteCryptoKeyManager.PasswordWrap(wrapped, salt, iv),
                password,
            )
            val keystoreWrap = keyManager.wrapDekWithKeystore(dekBytes)
            store.update {
                it.copy(
                    enabled = true,
                    keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64,
                    keystoreWrapIvBase64 = keystoreWrap.ivBase64,
                )
            }
            dek = dekBytes
        }
    }

    suspend fun changePassword(oldPassword: CharArray, newPassword: CharArray): Result<Unit> {
        require(newPassword.size >= 8) { "New password must be at least 8 characters." }
        return runCatching {
            unlockWithPassword(oldPassword).getOrThrow()
            val dekBytes = dek ?: error("Encryption not unlocked.")
            val passwordWrap = keyManager.wrapDekWithPassword(dekBytes, newPassword)
            store.update {
                it.copy(
                    passwordWrappedDekBase64 = passwordWrap.wrappedDekBase64,
                    passwordWrapSaltBase64 = passwordWrap.saltBase64,
                    passwordWrapIvBase64 = passwordWrap.ivBase64,
                )
            }
        }
    }

    suspend fun applyCloudWrappedDek(state: NoteCryptoState): Result<Unit> = runCatching {
        mutex.withLock {
            require(state.enabled && state.passwordWrappedDekBase64 != null)
            val dekBytes = dek
            if (dekBytes != null) {
                val keystoreWrap = keyManager.wrapDekWithKeystore(dekBytes)
                store.update {
                    state.copy(
                        keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64,
                        keystoreWrapIvBase64 = keystoreWrap.ivBase64,
                    )
                }
            } else {
                store.update { state }
            }
        }
    }

    suspend fun cloudExportState(): NoteCryptoState? {
        val saved = store.current()
        if (!saved.enabled || saved.passwordWrappedDekBase64 == null) return null
        return saved.copy(
            keystoreWrappedDekBase64 = null,
            keystoreWrapIvBase64 = null,
        )
    }

    suspend fun lock() {
        mutex.withLock {
            dek?.fill(0)
            dek = null
        }
    }

    suspend fun resetStoredCrypto() {
        mutex.withLock {
            dek?.fill(0)
            dek = null
            store.update { NoteCryptoState() }
        }
    }

    override fun encryptForStorage(plain: String): String {
        val trimmed = plain.trim()
        if (trimmed.isEmpty()) return ""
        val key = dek ?: return plain
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(trimmed.encodeToByteArray())
        val payload = iv + ciphertext
        return CIPHER_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decryptFromStorage(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith(CIPHER_PREFIX)) return stored
        val key = dek ?: return ENCRYPTED_PLACEHOLDER
        val payload = Base64.decode(stored.removePrefix(CIPHER_PREFIX), Base64.NO_WRAP)
        if (payload.size <= IV_BYTES) return ENCRYPTED_PLACEHOLDER
        val iv = payload.copyOfRange(0, IV_BYTES)
        val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).decodeToString()
    }

    companion object {
        const val CIPHER_PREFIX = "mgenc1:"
        const val ENCRYPTED_PLACEHOLDER = "🔒 Encrypted note — unlock in Settings"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
