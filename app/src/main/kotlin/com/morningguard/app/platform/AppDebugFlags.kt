package com.morningguard.app.platform

import com.morningguard.app.BuildConfig

/**
 * Screenshot builds are debuggable for automation but hide debug-only UI so captures match release.
 */
object AppDebugFlags {
    val screenshotMode: Boolean = BuildConfig.SCREENSHOT_MODE

    val presenterMode: Boolean = PresenterMode.enabled

    /** Debug cards, step debug overlay, etc. */
    val showDebugPanels: Boolean = BuildConfig.DEBUG && !PresenterMode.enabled

    /** Noisy logging / debug analytics — off in release and screenshot captures. */
    val verboseDiagnostics: Boolean = BuildConfig.DEBUG && !PresenterMode.enabled
}
