package com.morningguard.data.security

object RecoveryPasswordRules {
    const val MIN_LENGTH = 8

    fun validate(password: CharArray): String? {
        if (password.size < MIN_LENGTH) {
            return "Use at least $MIN_LENGTH characters."
        }
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        if (!hasLetter || !hasDigit) {
            return "Include at least one letter and one number."
        }
        return null
    }
}
