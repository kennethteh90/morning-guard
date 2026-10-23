package com.morningguard.app.screenshot

object ScreenshotIntents {
    const val EXTRA_SCENE = "screenshot_scene"

    /** Broadcast when [EXTRA_SCENE] navigation and demo seed have settled. */
    const val ACTION_SCENE_READY = "com.morningguard.app.action.SCREENSHOT_SCENE_READY"
    const val EXTRA_READY_SCENE = "ready_scene"

    const val SCENE_HOME = "home"
    const val SCENE_DIGEST = "digest"
    const val SCENE_GOAL = "goal"
    const val SCENE_STATS = "stats"
    /** Onboarding step: notification access disclosure (Play notification-listener demo). */
    const val SCENE_ONBOARDING_NOTIFICATION = "onboarding_notification"
    const val SCENE_SETTINGS = "settings"
    const val SCENE_BYPASS = "bypass"
    const val SCENE_EMERGENCY = "emergency"
    const val SCENE_HIGHLIGHTS = "highlights"

    val allScenes = listOf(
        SCENE_HOME,
        SCENE_DIGEST,
        SCENE_GOAL,
        SCENE_STATS,
        SCENE_ONBOARDING_NOTIFICATION,
        SCENE_SETTINGS,
        SCENE_BYPASS,
        SCENE_EMERGENCY,
        SCENE_HIGHLIGHTS,
    )
}
