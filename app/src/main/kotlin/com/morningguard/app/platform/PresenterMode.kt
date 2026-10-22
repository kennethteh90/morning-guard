package com.morningguard.app.platform

import com.morningguard.app.BuildConfig

/**
 * Store-capture automation ([BuildConfig.SCREENSHOT_MODE]) and the installable demo app
 * ([BuildConfig.DEMO_MODE]) share relaxed gates and demo seeding.
 */
object PresenterMode {
    val enabled: Boolean = BuildConfig.SCREENSHOT_MODE || BuildConfig.DEMO_MODE

    val demoApp: Boolean = BuildConfig.DEMO_MODE
}
