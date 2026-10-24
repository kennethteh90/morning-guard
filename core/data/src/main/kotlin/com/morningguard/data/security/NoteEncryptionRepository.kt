package com.morningguard.data.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class NoteEncryptionRepository @Inject constructor(
    private val store: NoteCryptoStore,
    private val keyManager: NoteCryptoKeyManager,
) : HighlightNoteCipher {

    private val mutex = Mutex()
    @Volatile
    private var dek: ByteArray? = null

    val cryptoState: Flow<NoteCryptoState> = store.state

    /** True when the DEK is in memory and notes can be encrypted or decrypted. */
    fun isDekUnlocked(): Boolean = dek != null

    override fun isEncryptionEnabled(): Boolean = isDekUnlocked()

    suspend fun state(): NoteCryptoState = store.current()

    suspend fun hasRecoveryPassword(): Boolean = store.current().hasRecoveryPassword()

    suspend fun requiresCloudRecovery(): Boolean = store.current().hasPendingCloudRecovery()

    suspend fun ensureDekReady(): Result<Unit> = runCatching {
        mutex.withLock {
            if (dek != null) return@withLock
            val saved = store.current()
            if (saved.enabled) {
                unlockFromKeystoreInternal(saved)?.let {
                    dek = it
                    return@withLock
                }
            }
            val dekBytes = keyManager.generateDek()
            val keystoreWrap = keyManager.wrapDekWithKeystore(dekBytes)
            store.update { current ->
                NoteCryptoState(
                    enabled = true,
                    passwordWrappedDekBase64 = current.passwordWrappedDekBase64,
                    passwordWrapSaltBase64 = current.passwordWrapSaltBase64,
                    passwordWrapIvBase64 = current.passwordWrapIvBase64,
                    keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64,
                    keystoreWrapIvBase64 = keystoreWrap.ivBase64,
                    pendingCloudPasswordWrappedDekBase64 = current.pendingCloudPasswordWrappedDekBase64,
                    pendingCloudPasswordWrapSaltBase64 = current.pendingCloudPasswordWrapSaltBase64,
                    pendingCloudPasswordWrapIvBase64 = current.pendingCloudPasswordWrapIvBase64,
                    cloudRecoveryRequired = current.cloudRecoveryRequired,
                )
            }
            dek = dekBytes
        }
    }

    suspend fun tryUnlockFromKeystore(): Boolean = mutex.withLock {
        val saved = store.current()
        if (!saved.enabled) return false
        val bytes = unlockFromKeystoreInternal(saved) ?: return false
        dek = bytes
        true
    }

    private fun unlockFromKeystoreInternal(saved: NoteCryptoState): ByteArray? {
        val wrapped = saved.keystoreWrappedDekBase64 ?: return null
        val iv = saved.keystoreWrapIvBase64 ?: return null
        return runCatching {
            keyManager.unwrapDekWithKeystore(NoteCryptoKeyManager.KeystoreWrap(wrapped, iv))
        }.getOrNull()
    }

    /**
     * First-time or additional wrap of the current DEK with a recovery password (same DEK, no note re-encryption).
     */
    suspend fun setRecoveryPassword(password: CharArray): Result<Unit> {
        require(password.size >= 8) { "Password must be at least 8 characters." }
        return runCatching {
            ensureDekReady().getOrThrow()
            mutex.withLock {
                val dekBytes = dek ?: error("Encryption key is not available.")
                val passwordWrap = keyManager.wrapDekWithPassword(dekBytes, password)
                store.update {
                    it.copy(
                        enabled = true,
                        passwordWrappedDekBase64 = passwordWrap.wrappedDekBase64,
                        passwordWrapSaltBase64 = passwordWrap.saltBase64,
                        passwordWrapIvBase64 = passwordWrap.ivBase64,
                    )
                }
            }
        }
    }

    /** @deprecated Use [setRecoveryPassword]; kept for settings UI naming. */
    suspend fun setupWithPassword(password: CharArray): Result<Unit> = setRecoveryPassword(password)

    suspend fun unlockWithPassword(password: CharArray): Result<Unit> = runCatching {
        mutex.withLock {
            val saved = store.current()
            val (wrapped, salt, iv) = when {
                saved.hasPendingCloudRecovery() -> Triple(
                    saved.pendingCloudPasswordWrappedDekBase64,
                    saved.pendingCloudPasswordWrapSaltBase64,
                    saved.pendingCloudPasswordWrapIvBase64,
                )
                else -> Triple(
                    saved.passwordWrappedDekBase64,
                    saved.passwordWrapSaltBase64,
                    saved.passwordWrapIvBase64,
                )
            }
            val wrappedNonNull = wrapped ?: error("Recovery password is not set up for this account.")
            val saltNonNull = salt ?: error("Missing encryption salt.")
            val ivNonNull = iv ?: error("Missing encryption metadata.")
            val cloudDekBytes = keyManager.unwrapDekWithPassword(
                NoteCryptoKeyManager.PasswordWrap(wrappedNonNull, saltNonNull, ivNonNull),
                password,
            )
            dek?.takeIf { !it.contentEquals(cloudDekBytes) }?.fill(0)
            val keystoreWrap = keyManager.wrapDekWithKeystore(cloudDekBytes)
            store.update {
                NoteCryptoState(
                    enabled = true,
                    passwordWrappedDekBase64 = wrappedNonNull,
                    passwordWrapSaltBase64 = saltNonNull,
                    passwordWrapIvBase64 = ivNonNull,
                    keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64,
                    keystoreWrapIvBase64 = keystoreWrap.ivBase64,
                    pendingCloudPasswordWrappedDekBase64 = null,
                    pendingCloudPasswordWrapSaltBase64 = null,
                    pendingCloudPasswordWrapIvBase64 = null,
                    cloudRecoveryRequired = false,
                )
            }
            dek = cloudDekBytes
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
            val local = store.current()
            val localHasPassword = local.hasRecoveryPassword()
            val localAutoDeviceKey = local.enabled && !localHasPassword

            if (localAutoDeviceKey) {
                store.update {
                    it.copy(
                        pendingCloudPasswordWrappedDekBase64 = state.passwordWrappedDekBase64,
                        pendingCloudPasswordWrapSaltBase64 = state.passwordWrapSaltBase64,
                        pendingCloudPasswordWrapIvBase64 = state.passwordWrapIvBase64,
                        cloudRecoveryRequired = true,
                    )
                }
                return@withLock
            }

            val dekBytes = dek
            if (dekBytes != null) {
                val keystoreWrap = keyManager.wrapDekWithKeystore(dekBytes)
                store.update {
                    state.copy(
                        keystoreWrappedDekBase64 = keystoreWrap.wrappedDekBase64,
                        keystoreWrapIvBase64 = keystoreWrap.ivBase64,
                        pendingCloudPasswordWrappedDekBase64 = null,
                        pendingCloudPasswordWrapSaltBase64 = null,
                        pendingCloudPasswordWrapIvBase64 = null,
                        cloudRecoveryRequired = false,
                    )
                }
            } else {
                store.update {
                    state.copy(
                        pendingCloudPasswordWrappedDekBase64 = null,
                        pendingCloudPasswordWrapSaltBase64 = null,
                        pendingCloudPasswordWrapIvBase64 = null,
                        cloudRecoveryRequired = false,
                    )
                }
            }
        }
    }

    suspend fun cloudExportState(): NoteCryptoState? {
        val saved = store.current()
        if (!saved.enabled || !saved.hasRecoveryPassword()) return null
        if (saved.cloudRecoveryRequired) return null
        return saved.copy(
            keystoreWrappedDekBase64 = null,
            keystoreWrapIvBase64 = null,
            pendingCloudPasswordWrappedDekBase64 = null,
            pendingCloudPasswordWrapSaltBase64 = null,
            pendingCloudPasswordWrapIvBase64 = null,
            cloudRecoveryRequired = false,
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
        val key = dek ?: error("Notes encryption key is locked. Call ensureDekReady() first.")
        return NoteContentCipher.encrypt(plain, key)
    }

    override fun decryptFromStorage(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!NoteContentCipher.isCiphertext(stored)) return stored
        val key = dek ?: return NoteContentCipher.ENCRYPTED_PLACEHOLDER
        return runCatching { NoteContentCipher.decrypt(stored, key) }
            .getOrDefault(NoteContentCipher.ENCRYPTED_PLACEHOLDER)
    }

    suspend fun reencryptPlaintextValue(stored: String): String {
        if (stored.isEmpty() || NoteContentCipher.isCiphertext(stored)) return stored
        ensureDekReady().getOrThrow()
        return encryptForStorage(stored)
    }

    companion object {
        const val CIPHER_PREFIX = NoteContentCipher.CIPHER_PREFIX
        const val ENCRYPTED_PLACEHOLDER = NoteContentCipher.ENCRYPTED_PLACEHOLDER
    }
}
