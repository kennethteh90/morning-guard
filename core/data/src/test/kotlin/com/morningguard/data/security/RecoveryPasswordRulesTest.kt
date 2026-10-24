package com.morningguard.data.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryPasswordRulesTest {

    @Test
    fun `accepts letter and digit`() {
        assertNull(RecoveryPasswordRules.validate("secure1".toCharArray()))
    }

    @Test
    fun `rejects short password`() {
        assertNotNull(RecoveryPasswordRules.validate("abc1".toCharArray()))
    }
}
