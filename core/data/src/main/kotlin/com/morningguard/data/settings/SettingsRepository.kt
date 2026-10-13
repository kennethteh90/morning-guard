package com.morningguard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.morningguard.domain.model.AlarmSettings
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningGoalProgress
import com.morningguard.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.datastore.core.Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userSettingsDataStore: DataStore<UserSettings> by dataStore(
    fileName = "user_settings.json",
    serializer = UserSettingsSerializer,
)

private val Context.goalProgressDataStore: DataStore<GoalProgressStore> by dataStore(
    fileName = "goal_progress.json",
    serializer = GoalProgressSerializer,
)

@Serializable
data class GoalProgressStore(
    val progress: MorningGoalProgress? = null,
    val emergencyBypassUntilEpochMs: Long? = null,
    val emergencyPassphraseHash: String? = null,
    val emergencyPassphraseSalt: String? = null,
)

object GoalProgressSerializer : Serializer<GoalProgressStore> {
    override val defaultValue = GoalProgressStore()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readFrom(input: InputStream): GoalProgressStore {
        return json.decodeFromString(GoalProgressStore.serializer(), input.readBytes().decodeToString())
    }

    override suspend fun writeTo(t: GoalProgressStore, output: OutputStream) {
        output.write(json.encodeToString(GoalProgressStore.serializer(), t).encodeToByteArray())
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<UserSettings> = context.userSettingsDataStore.data

    val goalProgressStore: Flow<GoalProgressStore> = context.goalProgressDataStore.data

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        context.userSettingsDataStore.updateData(transform)
    }

    suspend fun setBypassPackages(packages: Set<String>) {
        updateSettings { it.copy(bypassPackageNames = packages) }
    }

    suspend fun setMorningGoal(config: MorningGoalConfig) {
        updateSettings { it.copy(morningGoal = config) }
    }

    suspend fun setAlarm(alarm: AlarmSettings) {
        updateSettings { it.copy(alarm = alarm) }
    }

    suspend fun updateGoalProgress(transform: (GoalProgressStore) -> GoalProgressStore) {
        context.goalProgressDataStore.updateData(transform)
    }

    suspend fun currentSettings(): UserSettings = settings.first()

    suspend fun currentGoalStore(): GoalProgressStore = goalProgressStore.first()
}
