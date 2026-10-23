package com.morningguard.domain.bible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BibleCanonTest {

    @Test
    fun `advance within book`() {
        assertEquals("John" to 2, BibleCanon.advance("John", 1))
    }

    @Test
    fun `advance across books`() {
        assertEquals("Acts" to 1, BibleCanon.advance("John", 21))
    }

    @Test
    fun `previous within book`() {
        assertEquals("John" to 2, BibleCanon.previous("John", 3))
    }
}
