package com.morningguard.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val KEY_ALIAS = "morningguard_supabase_session"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_SIZE_BITS = 256
private const val GCM_IV_LENGTH_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128

internal interface SessionBlobCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}

internal object AndroidKeystoreSessionCipher : SessionBlobCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return iv + ciphertext
    }

    override fun decrypt(data: ByteArray): ByteArray {
        require(data.size > GCM_IV_LENGTH_BYTES) { "Invalid encrypted session blob" }
        val iv = data.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH_BYTES, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE,
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
            keyGenerator.generateKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
}

internal class AesGcmSessionCipher(
    private val secretKey: SecretKey,
) : SessionBlobCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return iv + ciphertext
    }

    override fun decrypt(data: ByteArray): ByteArray {
        require(data.size > GCM_IV_LENGTH_BYTES) { "Invalid encrypted session blob" }
        val iv = data.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH_BYTES, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }
}

@Singleton
class KeystoreSessionManager internal constructor(
    private val context: Context,
    private val cipher: SessionBlobCipher,
) : SessionManager {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, AndroidKeystoreSessionCipher)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val sessionFile: File
        get() = File(context.filesDir, SESSION_FILE_NAME)

    override suspend fun saveSession(session: UserSession) {
        withContext(Dispatchers.IO) {
            val plaintext = json.encodeToString(UserSession.serializer(), session)
            sessionFile.writeBytes(cipher.encrypt(plaintext.encodeToByteArray()))
        }
    }

    override suspend fun loadSession(): UserSession? {
        return withContext(Dispatchers.IO) {
            val file = sessionFile
            if (!file.exists()) {
                return@withContext null
            }
            val plaintext = cipher.decrypt(file.readBytes()).decodeToString()
            json.decodeFromString(UserSession.serializer(), plaintext)
        }
    }

    override suspend fun deleteSession() {
        withContext(Dispatchers.IO) {
            sessionFile.delete()
        }
    }

    internal companion object {
        const val SESSION_FILE_NAME = "supabase_auth_session.enc"

        fun testCipher(): SessionBlobCipher {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(KEY_SIZE_BITS)
            return AesGcmSessionCipher(keyGenerator.generateKey())
        }
    }
}
