package com.seasaw.theta.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryEscapeTest {
    @Test
    fun `like wildcards are neutralised`() {
        assertEquals("100\\%", HistoryRepository.escapeLike("100%"))
        assertEquals("a\\_b", HistoryRepository.escapeLike("a_b"))
        assertEquals("c:\\\\x", HistoryRepository.escapeLike("c:\\x"))
        assertEquals("plain", HistoryRepository.escapeLike("plain"))
    }
}
