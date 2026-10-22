package com.morningguard.domain.bible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BibleBookUsfmTest {

    @Test
    fun `chapter id for John`() {
        assertEquals("JHN.3", BibleBookUsfm.chapterId("John", 3))
    }

    @Test
    fun `chapter id for numbered book`() {
        assertEquals("1SA.31", BibleBookUsfm.chapterId("1 Samuel", 31))
    }
}
