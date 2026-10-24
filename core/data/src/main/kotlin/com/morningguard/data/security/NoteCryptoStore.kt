package com.morningguard.data.security

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class NoteCryptoState(
    val enabled: Boolean = false,
    val passwordWrappedDekBase64: String? = null,
    val passwordWrapSaltBase64: String? = null,
    val passwordWrapIvBase64: String? = null,
    val keystoreWrappedDekBase64: String? = null,
    val keystoreWrapIvBase64: String? = null,
    /** Cloud password wrap waiting for user entry (multi-device / new install). */
    val pendingCloudPasswordWrappedDekBase64: String? = null,
    val pendingCloudPasswordWrapSaltBase64: String? = null,
    val pendingCloudPasswordWrapIvBase64: String? = null,
    val cloudRecoveryRequired: Boolean = false,
) {
    fun hasRecoveryPassword(): Boolean = !passwordWrappedDekBase64.isNullOrBlank()

    fun hasPendingCloudRecovery(): Boolean =
        cloudRecoveryRequired &&
            !pendingCloudPasswordWrappedDekBase64.isNullOrBlank() &&
            !pendingCloudPasswordWrapSaltBase64.isNullOrBlank() &&
            !pendingCloudPasswordWrapIvBase64.isNullOrBlank()
}

private object NoteCryptoStateSerializer : Serializer<NoteCryptoState> {
    override val defaultValue = NoteCryptoState()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readFrom(input: InputStream): NoteCryptoState {
        try {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) return defaultValue
            return json.decodeFromString(NoteCryptoState.serializer(), text)
        } catch (e: SerializationException) {
            throw CorruptionException("Unable to read NoteCryptoState", e)
        }
    }

    override suspend fun writeTo(t: NoteCryptoState, output: OutputStream) {
        output.write(json.encodeToString(NoteCryptoState.serializer(), t).encodeToByteArray())
    }
}

private val Context.noteCryptoDataStore: DataStore<NoteCryptoState> by dataStore(
    fileName = "note_crypto.json",
    serializer = NoteCryptoStateSerializer,
)

@Singleton
class NoteCryptoStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.noteCryptoDataStore

    val state = dataStore.data

    suspend fun current(): NoteCryptoState = dataStore.data.first()

    suspend fun update(transform: (NoteCryptoState) -> NoteCryptoState) {
        dataStore.updateData(transform)
    }
}
