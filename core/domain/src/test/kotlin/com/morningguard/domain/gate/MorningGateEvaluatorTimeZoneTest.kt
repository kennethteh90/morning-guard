package com.morningguard.domain.gate

import com.morningguard.domain.model.MorningWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MorningGateEvaluatorTimeZoneTest {

    private val window = MorningWindow(startHour = 6, startMinute = 0, endHour = 9, endMinute = 0)

    @Test
    fun morningWindowUsesLocalWallClock_afterZoneChangeFromSingaporeToVienna() {
        val instant = ZonedDateTime.of(2026, 11, 22, 7, 30, 0, 0, ZoneId.of("Europe/Vienna")).toInstant()
        val nowMs = instant.toEpochMilli()
        var zone = ZoneId.of("Asia/Singapore")
        val evaluator = MorningGateEvaluator(zoneIdProvider = { zone }) { nowMs }

        // 07:30 Vienna is 14:30 Singapore — outside the morning window when still using SG zone.
        assertEquals(GateState.INACTIVE, evaluator.evaluate(window, null, null, null).state)

        zone = ZoneId.of("Europe/Vienna")
        val travelEvaluation = evaluator.evaluate(window, null, null, null)
        assertEquals(GateState.LOCKED, travelEvaluation.state)
        assertEquals("2026-11-22", travelEvaluation.dateKey)
        assertEquals("morning_window", travelEvaluation.reason)
    }

    @Test
    fun dateKeyAdvancesWithDeviceZone_notSetupZone() {
        val instant = ZonedDateTime.of(2026, 11, 21, 18, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        val nowMs = instant.toEpochMilli()
        var zone = ZoneId.of("Asia/Singapore")
        val evaluator = MorningGateEvaluator(zoneIdProvider = { zone }) { nowMs }

        assertEquals("2026-11-22", evaluator.evaluate(window, null, null, null).dateKey)

        zone = ZoneId.of("Europe/Vienna")
        assertEquals("2026-11-21", evaluator.evaluate(window, null, null, null).dateKey)
    }
}
