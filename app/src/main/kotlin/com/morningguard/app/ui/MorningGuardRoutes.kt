package com.morningguard.app.ui

object MorningGuardRoutes {
    const val HOME = "home"
    const val GOAL = "goal"
    const val GOAL_SUCCESS = "goal_success"
    const val DIGEST = "digest"
    const val SETTINGS = "settings"
    const val BYPASS = "bypass"
    const val EMERGENCY = "emergency"
    const val AUTH = "auth"
    const val STATS = "stats"
    const val HIGHLIGHTS = "highlights"
    const val BIBLE_READER = "bible_reader"

    fun goalSuccess(heldCount: Int): String = "$GOAL_SUCCESS/$heldCount"
}
