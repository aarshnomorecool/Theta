package com.seasaw.theta.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LogScrubberTest {
    @Test
    fun `urls are removed`() {
        assertEquals("failed to load [url] now", LogScrubber.scrub("failed to load https://bank.com/acct?id=9 now"))
        assertFalse(LogScrubber.scrub("intent://scan/#Intent;end").contains("scan"))
        assertFalse(LogScrubber.scrub("pay upi://pay?pa=me@bank").contains("me@bank"))
    }

    @Test
    fun `bare hosts and query strings are removed`() {
        assertEquals("host [host] down", LogScrubber.scrub("host news.example.co.in down"))
        assertFalse(LogScrubber.scrub("path /a?token=abc").contains("abc"))
    }

    @Test
    fun `ordinary text is kept`() {
        val text = "Room failed: no such column visitCount"
        assertEquals(text, LogScrubber.scrub(text))
    }

    @Test
    fun `clean throwables are returned untouched`() {
        val original = IllegalStateException("bad state")
        assertSame(original, LogScrubber.scrub(original))
    }

    @Test
    fun `throwables with urls are rewritten keeping type name and stack`() {
        val original = IllegalArgumentException("cannot open https://secret.example.com/x")
        val scrubbed = LogScrubber.scrub(original)
        assertFalse(scrubbed.message!!.contains("secret"))
        assertTrue(scrubbed.message!!.contains("IllegalArgumentException"))
        assertEquals(original.stackTrace.toList(), scrubbed.stackTrace.toList())
    }

    @Test
    fun `causes are scrubbed too`() {
        val cause = RuntimeException("GET https://secret.example.com failed")
        val outer = IllegalStateException("wrapper", cause)
        val scrubbed = LogScrubber.scrub(outer)
        assertFalse(scrubbed.cause!!.message!!.contains("secret"))
    }

    @Test
    fun `cyclic cause chains terminate`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        LogScrubber.scrub(a)
    }
}
