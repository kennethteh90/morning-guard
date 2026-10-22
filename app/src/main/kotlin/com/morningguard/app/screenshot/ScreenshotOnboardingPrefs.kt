package com.morningguard.app.screenshot

import com.morningguard.app.ui.onboarding.OnboardingStep

/** In-memory overrides for screenshot / Play policy demo captures (not persisted). */
object ScreenshotOnboardingPrefs {
    var initialStep: OnboardingStep? = null
    var disclosureAccepted: Boolean = false

    fun clear() {
        initialStep = null
        disclosureAccepted = false
    }
}
