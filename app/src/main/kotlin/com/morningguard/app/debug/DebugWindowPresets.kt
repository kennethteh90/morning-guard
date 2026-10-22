package com.morningguard.app.debug

import com.morningguard.domain.model.MorningWindow
import java.time.LocalTime

object DebugWindowPresets {
    val defaultProduction: MorningWindow = MorningWindow(1, 0, 10, 0)

    /** Window that includes the current clock time (± buffer) for listener testing. */
    fun includingNow(
        now: LocalTime = LocalTime.now(),
        minutesBefore: Long = 30,
        hoursAfter: Long = 3,
    ): MorningWindow {
        val start = now.minusMinutes(minutesBefore)
        val end = now.plusHours(hoursAfter)
        return MorningWindow(
            startHour = start.hour,
            startMinute = start.minute,
            endHour = end.hour,
            endMinute = end.minute,
        )
    }

    /** Gate stays active almost all day until goal complete (debug only). */
    fun fullDayTest(): MorningWindow = MorningWindow(0, 0, 23, 59)
}
