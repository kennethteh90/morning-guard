package com.morningguard.domain.security

class EmergencyBypassPolicy(
    private val minLength: Int = 8,
    private val bypassDurationMs: Long = 60 * 60 * 1000L,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    fun validateLength(passphrase: String): Boolean = passphrase.length >= minLength

    fun bypassExpiresAt(nowMs: Long = nowProvider()): Long = nowMs + bypassDurationMs

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
