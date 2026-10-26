package com.morningguard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.morningguard.domain.bible.BibleCanon
import com.morningguard.domain.bible.BibleTranslations
import com.morningguard.domain.model.AlarmSettings
import com.morningguard.domain.model.DefaultBypassPackages
import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningGoalProgress
import com.morningguard.domain.history.MorningDayOutcome
import com.morningguard.domain.history.MorningDayRecord
import com.morningguard.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
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
    val failedAttempts: Int = 0,
    val lockedUntilEpochMs: Long? = null,
    val morningHistory: List<MorningDayRecord> = emptyList(),
    /** Latest foreground goal-screen seconds per calendar date (local + merged from sync). */
    val goalTimeSecondsByDate: Map<String, Int> = emptyMap(),
    /** Foreground seconds in the Bible reader per calendar date (morning goal or Read Bible). */
    val bibleReadingSecondsByDate: Map<String, Int> = emptyMap(),
    val goalStepsByDate: Map<String, Int> = emptyMap(),
    /** When set and in the future, notifications are held outside the morning window. */
    val peaceSessionUntilEpochMs: Long? = null,
)

object GoalProgressSerializer : Serializer<GoalProgressStore> {
    override val defaultValue = GoalProgressStore()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readFrom(input: InputStream): GoalProgressStore {
        try {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) return defaultValue
            return json.decodeFromString(GoalProgressStore.serializer(), text)
        } catch (e: SerializationException) {
            throw CorruptionException("Unable to read GoalProgressStore", e)
        }
    }

    override suspend fun writeTo(t: GoalProgressStore, output: OutputStream) {
        output.write(json.encodeToString(GoalProgressStore.serializer(), t).encodeToByteArray())
    }
}

@Singleton
@Suppress("TooManyFunctions")
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

    suspend fun ensureDefaultBypassPackages() {
        updateSettings { settings ->
            val merged = settings.bypassPackageNames + DefaultBypassPackages.packageNames
            if (merged.size == settings.bypassPackageNames.size) settings
            else settings.copy(bypassPackageNames = merged)
        }
    }

    suspend fun setBibleTranslation(translationId: String) {
        val normalized = BibleTranslations.normalizeId(translationId)
        updateSettings { settings ->
            settings.copy(
                morningGoal = settings.morningGoal.copy(bibleTranslation = normalized),
            )
        }
    }

    suspend fun setBibleReading(book: String, chapter: Int) {
        val normalizedBook = BibleCanon.normalizeBook(book)
        val clampedChapter = chapter.coerceIn(1, BibleCanon.chapterCount(normalizedBook))
        updateSettings { settings ->
            settings.copy(
                morningGoal = settings.morningGoal.copy(
                    bibleBook = normalizedBook,
                    bibleChapter = clampedChapter,
                ),
            )
        }
    }

    suspend fun advanceBibleReadingAfterComplete() {
        updateSettings { settings ->
            val goal = settings.morningGoal
            if (goal.type != GoalType.BIBLE_CHAPTER) return@updateSettings settings
            val (nextBook, nextChapter) = BibleCanon.advance(goal.bibleBook, goal.bibleChapter)
            settings.copy(
                morningGoal = goal.copy(
                    bibleBook = nextBook,
                    bibleChapter = nextChapter,
                ),
            )
        }
    }

    suspend fun recordMorningOutcome(
        dateKey: String,
        outcome: MorningDayOutcome,
        trackGoalTime: Boolean = true,
        stepsOnGoal: Int = 0,
    ) {
        updateGoalProgress { store ->
            val seconds = if (trackGoalTime) goalTimeForDate(store, dateKey) else 0
            val steps = stepsOnGoal.coerceAtLeast(0)
            store.copy(
                morningHistory = mergeMorningRecord(
                    store.morningHistory,
                    dateKey,
                    outcome,
                    timeOnGoalSeconds = seconds,
                    stepsOnGoal = steps,
                ),
            )
        }
    }

    /** Keeps the best step count seen for [dateKey] (step goal days). */
    suspend fun recordGoalStepsForDate(dateKey: String, steps: Int) {
        if (steps <= 0) return
        updateGoalProgress { store ->
            val merged = mergeGoalSteps(store.goalStepsByDate, dateKey, steps)
            if (merged == store.goalStepsByDate) store else store.copy(goalStepsByDate = merged)
        }
    }

    suspend fun recordBibleReadingSeconds(dateKey: String, seconds: Int) {
        if (seconds <= 0) return
        updateGoalProgress { store ->
            val merged = mergeGoalTime(store.bibleReadingSecondsByDate, dateKey, seconds)
            if (merged == store.bibleReadingSecondsByDate) store else store.copy(bibleReadingSecondsByDate = merged)
        }
    }

    suspend fun mergeMorningHistoryFromRemote(remoteRecords: List<MorningDayRecord>) {
        if (remoteRecords.isEmpty()) return
        updateGoalProgress { store ->
            var history = store.morningHistory
            var times = store.goalTimeSecondsByDate
            for (record in remoteRecords) {
                history = mergeMorningRecord(
                    history,
                    record.dateKey,
                    record.outcome,
                    record.recordedAtEpochMs,
                    record.timeOnGoalSeconds,
                )
                times = mergeGoalTime(times, record.dateKey, record.timeOnGoalSeconds)
            }
            store.copy(morningHistory = history, goalTimeSecondsByDate = times)
        }
    }

    suspend fun boundUserId(): String? = currentSettings().syncBoundUserId

    suspend fun setBoundUserId(userId: String?) {
        updateSettings { it.copy(syncBoundUserId = userId) }
    }

    suspend fun clearMorningHistory() {
        updateGoalProgress { store ->
            store.copy(
                morningHistory = emptyList(),
                goalTimeSecondsByDate = emptyMap(),
                bibleReadingSecondsByDate = emptyMap(),
                goalStepsByDate = emptyMap(),
                emergencyPassphraseHash = null,
                emergencyPassphraseSalt = null,
                emergencyBypassUntilEpochMs = null,
                failedAttempts = 0,
                lockedUntilEpochMs = null,
            )
        }
    }

    suspend fun clearSensitiveGoalData() {
        updateGoalProgress { GoalProgressStore() }
    }

    private fun mergeMorningRecord(
        history: List<MorningDayRecord>,
        dateKey: String,
        outcome: MorningDayOutcome,
        recordedAtEpochMs: Long = System.currentTimeMillis(),
        timeOnGoalSeconds: Int = 0,
        stepsOnGoal: Int = 0,
    ): List<MorningDayRecord> {
        val existing = history.find { it.dateKey == dateKey }
        if (existing?.outcome == MorningDayOutcome.GOAL_COMPLETED) {
            val betterTime = timeOnGoalSeconds > existing.timeOnGoalSeconds
            val betterSteps = stepsOnGoal > existing.stepsOnGoal
            if (!betterTime && !betterSteps) return history
            return history.map {
                if (it.dateKey == dateKey) {
                    it.copy(
                        timeOnGoalSeconds = maxOf(it.timeOnGoalSeconds, timeOnGoalSeconds),
                        stepsOnGoal = maxOf(it.stepsOnGoal, stepsOnGoal),
                    )
                } else {
                    it
                }
            }
        }
        if (existing?.outcome == outcome) {
            val betterTime = timeOnGoalSeconds > existing.timeOnGoalSeconds
            val betterSteps = stepsOnGoal > existing.stepsOnGoal
            if (!betterTime && !betterSteps) return history
            return history.map {
                if (it.dateKey == dateKey) {
                    it.copy(
                        timeOnGoalSeconds = maxOf(it.timeOnGoalSeconds, timeOnGoalSeconds),
                        stepsOnGoal = maxOf(it.stepsOnGoal, stepsOnGoal),
                    )
                } else {
                    it
                }
            }
        }
        return history.filterNot { it.dateKey == dateKey } +
            MorningDayRecord(
                dateKey = dateKey,
                outcome = outcome,
                recordedAtEpochMs = recordedAtEpochMs,
                timeOnGoalSeconds = timeOnGoalSeconds,
                stepsOnGoal = stepsOnGoal,
            )
    }

    private fun goalTimeForDate(store: GoalProgressStore, dateKey: String): Int {
        val fromMap = store.goalTimeSecondsByDate[dateKey] ?: 0
        val fromProgress = store.progress
            ?.takeIf { it.dateKey == dateKey }
            ?.onScreenSeconds
            ?: 0
        return maxOf(fromMap, fromProgress)
    }

    private fun mergeGoalTime(
        map: Map<String, Int>,
        dateKey: String,
        seconds: Int,
    ): Map<String, Int> {
        if (seconds <= 0) return map
        val merged = maxOf(map[dateKey] ?: 0, seconds)
        return if (merged == map[dateKey]) map else map + (dateKey to merged)
    }

    private fun mergeGoalSteps(
        map: Map<String, Int>,
        dateKey: String,
        steps: Int,
    ): Map<String, Int> {
        if (steps <= 0) return map
        val merged = maxOf(map[dateKey] ?: 0, steps)
        return if (merged == map[dateKey]) map else map + (dateKey to merged)
    }

    suspend fun markRecoveryPasswordPromptShownAfterSignIn() {
        updateSettings { it.copy(recoveryPasswordPromptShownAfterSignIn = true) }
    }

    suspend fun recordRecoveryPasswordPromptDismissed() {
        updateSettings {
            it.copy(
                recoveryPasswordPromptShownAfterSignIn = true,
                recoveryPasswordLaterCount = it.recoveryPasswordLaterCount + 1,
                recoveryPasswordPromptLastDismissedEpochMs = System.currentTimeMillis(),
            )
        }
    }

    suspend fun incrementNoteSaveCountForRecoveryPrompt() {
        updateSettings { it.copy(noteSaveCountForRecoveryPrompt = it.noteSaveCountForRecoveryPrompt + 1) }
    }
}
