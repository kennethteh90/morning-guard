package com.morningguard.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Local civil-day helpers. Always resolves the device zone at call time so travel and
 * OS time-zone changes stay aligned with wall-clock semantics.
 */
object LocalDayClock {
    fun currentZoneId(): ZoneId = ZoneId.systemDefault()

    fun dateKeyForEpochMillis(epochMs: Long, zoneId: ZoneId = currentZoneId()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().toString()

    fun todayDateKey(zoneId: ZoneId = currentZoneId()): String =
        LocalDate.now(zoneId).toString()
}