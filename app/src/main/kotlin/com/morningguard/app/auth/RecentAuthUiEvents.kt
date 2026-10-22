package com.morningguard.app.auth

import javax.inject.Inject
import javax.inject.Singleton

/** One-shot UI signals after auth actions (e.g. magic-link deep link). */
@Singleton
class RecentAuthUiEvents @Inject constructor() {
    @Volatile
    var magicLinkJustCompleted: Boolean = false
        private set

    fun markMagicLinkCompleted() {
        magicLinkJustCompleted = true
    }

    fun consumeMagicLinkCompleted(): Boolean {
        if (!magicLinkJustCompleted) return false
        magicLinkJustCompleted = false
        return true
    }
}
