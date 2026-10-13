package com.morningguard.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.morningguard.domain.model.UserSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object UserSettingsSerializer : Serializer<UserSettings> {
    override val defaultValue: UserSettings = UserSettings()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readFrom(input: InputStream): UserSettings {
        try {
            return json.decodeFromString(UserSettings.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unable to read UserSettings", e)
        }
    }

    override suspend fun writeTo(t: UserSettings, output: OutputStream) {
        output.write(json.encodeToString(UserSettings.serializer(), t).encodeToByteArray())
    }
}
