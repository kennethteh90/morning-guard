package com.morningguard.domain.history

object MorningGoalTimeFormat {
    /** Compact duration for stats cards and history rows. */
    fun format(seconds: Int): String {
        if (seconds <= 0) return "0m"
        if (seconds < 60) return "${seconds}s"
        val totalMinutes = seconds / 60
        if (totalMinutes < 60) {
            val remSec = seconds % 60
            return if (remSec == 0) "${totalMinutes}m" else "${totalMinutes}m ${remSec}s"
        }
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (minutes == 0) "${hours}h" else "${hours}h ${minutes}m"
    }
}
