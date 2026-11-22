package com.morningguard.domain.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LocalDayClockTest {

    @Test
    fun dateKey_followsZone_whenTravelingFromSingaporeToVienna() {
        val instant = ZonedDateTime.of(2026, 11, 21, 18, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        val epochMs = instant.toEpochMilli()
        assertEquals(
            "2026-11-22",
            LocalDayClock.dateKeyForEpochMillis(epochMs, ZoneId.of("Asia/Singapore")),
        )
        assertEquals(
            "2026-11-21",
            LocalDayClock.dateKeyForEpochMillis(epochMs, ZoneId.of("Europe/Vienna")),
        )
    }

    @Test
    fun dateKey_respectsDstBoundaryInEuropeanZone() {
        // 2026-03-29 00:30 UTC is still previous local civil day in Vienna (CET, UTC+1).
        val beforeDst = ZonedDateTime.of(2026, 3, 29, 0, 30, 0, 0, ZoneId.of("UTC")).toInstant()
        assertEquals(
            "2026-03-29",
            LocalDayClock.dateKeyForEpochMillis(beforeDst.toEpochMilli(), ZoneId.of("Europe/Vienna")),
        )
        // 2026-03-29 01:30 UTC is same civil day after clocks spring forward (CEST, UTC+2).
        val afterDst = ZonedDateTime.of(2026, 3, 29, 1, 30, 0, 0, ZoneId.of("UTC")).toInstant()
        assertEquals(
            "2026-03-29",
            LocalDayClock.dateKeyForEpochMillis(afterDst.toEpochMilli(), ZoneId.of("Europe/Vienna")),
        )
    }
}
