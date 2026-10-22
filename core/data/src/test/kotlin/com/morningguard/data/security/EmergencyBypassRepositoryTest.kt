package com.morningguard.data.security

import com.morningguard.data.settings.GoalProgressStore
import com.morningguard.data.settings.SettingsRepository
import com.morningguard.domain.security.EmergencyBypassPolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyBypassRepositoryTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val hasher = PassphraseHasher()
    private val repository = EmergencyBypassRepository(
        settingsRepository,
        hasher,
        EmergencyBypassPolicy(bypassDurationMs = 3_600_000L),
        ZoneId.of("UTC"),
    )

    @Test
    fun `valid passphrase activates bypass`() = runTest {
        coEvery { settingsRepository.currentGoalStore() } returns GoalProgressStore()
        coEvery { settingsRepository.updateGoalProgress(any()) } coAnswers {
            val transform = firstArg<(GoalProgressStore) -> GoalProgressStore>()
            val store = transform(GoalProgressStore())
            coEvery { settingsRepository.currentGoalStore() } returns store
        }

        repository.setPassphrase("long-passphrase")
        val result = repository.attemptBypass("long-passphrase")
        assertTrue(result.isSuccess)
        coVerify(atLeast = 2) { settingsRepository.updateGoalProgress(any()) }
    }

    @Test
    fun `legacy passphrase format is rejected`() = runTest {
        coEvery { settingsRepository.currentGoalStore() } returns GoalProgressStore(
            emergencyPassphraseHash = "deadbeef",
            emergencyPassphraseSalt = "cafebabe",
        )

        val result = repository.attemptBypass("any-passphrase")
        assertTrue(result.isFailure)
        assertEquals(
            EmergencyBypassRepository.LEGACY_PASSPHRASE_MESSAGE,
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `lockout rejects bypass even with correct passphrase`() = runTest {
        val stored = hasher.hash("long-passphrase")
        val lockedUntil = System.currentTimeMillis() + EmergencyBypassPolicy.LOCKOUT_DURATION_MS
        coEvery { settingsRepository.currentGoalStore() } returns GoalProgressStore(
            emergencyPassphraseHash = stored,
            failedAttempts = 5,
            lockedUntilEpochMs = lockedUntil,
        )

        val result = repository.attemptBypass("long-passphrase")
        assertTrue(result.isFailure)
    }
}
