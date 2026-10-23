package com.morningguard.app.ui.goal

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class StepGoalDebugInfo(
    val activityPermission: Boolean = false,
    val hasStepCounter: Boolean = false,
    val hasStepDetector: Boolean = false,
    val counterListening: Boolean = false,
    val trackingActive: Boolean = false,
    val screenForeground: Boolean = false,
    val counterTotal: Int? = null,
    val stepsAtStart: Int? = null,
    val stepsAccumulated: Int = 0,
    val detectorPulses: Int = 0,
    val pollStepIncreases: Int = 0,
    val healthConnectAvailable: Boolean = false,
    val healthConnectPermission: Boolean = false,
    val healthConnectSteps: Int? = null,
    val lastEvent: String = "—",
) {
    fun toDisplayLines(): List<String> = listOf(
        "DEBUG steps · perm=$activityPermission counter=$hasStepCounter detector=$hasStepDetector",
        "listen=$counterListening track=$trackingActive fg=$screenForeground",
        "HC avail=$healthConnectAvailable perm=$healthConnectPermission hcSteps=${healthConnectSteps ?: "—"}",
        "total=${counterTotal ?: "—"} baseline=${stepsAtStart ?: "—"} accumulated=$stepsAccumulated",
        "detectorPulses=$detectorPulses pollAdds=$pollStepIncreases · last: $lastEvent",
    )
}

private val debugTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun stepDebugTimestamp(label: String): String = "$label @ ${debugTimeFormat.format(Instant.now())}"
