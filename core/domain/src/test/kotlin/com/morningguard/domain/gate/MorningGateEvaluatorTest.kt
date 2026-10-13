package com.morningguard.domain.gate

import com.morningguard.domain.model.GoalType
import com.morningguard.domain.model.MorningGoalConfig
import com.morningguard.domain.model.MorningGoalProgress
import com.morningguard.domain.model.MorningWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MorningGateEvaluatorTest {

    private val zoneId = ZoneId.of("UTC")
    private val sampleInstant = ZonedDateTime.of(2026, 10, 13, 7, 0, 0, 0, zoneId).toInstant().toEpochMilli()
    private val dateKey = "2026-10-13"

    @Test
    fun `unlock when goal complete today`() {
        val evaluator = MorningGateEvaluator(zoneId = zoneId) { sampleInstant }
        val progress = MorningGoalProgress(
            dateKey = dateKey,
            config = MorningGoalConfig(type = GoalType.TIMED_FOCUS),
            completedAtEpochMs = sampleInstant,
        )
        val result = evaluator.evaluate(MorningWindow(), progress, null)
        assertEquals(GateState.UNLOCKED, result.state)
    }

    @Test
    fun `locked during morning window without completion`() {
        val evaluator = MorningGateEvaluator(zoneId = zoneId) { sampleInstant }
        val result = evaluator.evaluate(
            MorningWindow(startHour = 6, endHour = 9),
            MorningGoalProgress(dateKey = dateKey, config = MorningGoalConfig(type = GoalType.TIMED_FOCUS)),
            null,
        )
        assertEquals(GateState.LOCKED, result.state)
    }

    @Test
    fun `emergency bypass unlocks`() {
        val evaluator = MorningGateEvaluator(zoneId = zoneId) { sampleInstant }
        val result = evaluator.evaluate(
            MorningWindow(startHour = 6, endHour = 9),
            null,
            emergencyBypassActiveUntilEpochMs = sampleInstant + 1000L,
        )
        assertEquals(GateState.UNLOCKED, result.state)
    }
}
