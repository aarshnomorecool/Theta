package com.seasaw.theta.core.common.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun `host is extracted and lowercased`() {
        assertEquals("example.com", UrlUtils.hostOf("https://EXAMPLE.com/a?b#c"))
        assertEquals("example.com", UrlUtils.hostOf("https://example.com:8443/a"))
        assertEquals("example.com", UrlUtils.hostOf("https://user:pw@example.com/a"))
        assertEquals("example.com", UrlUtils.hostOf("https://example.com?x=http://other.org"))
        assertEquals("[::1]", UrlUtils.hostOf("http://[::1]:3000/"))
    }

    @Test
    fun `backslash ends the host like it does in browsers`() {
        assertEquals("evil.com", UrlUtils.hostOf("https://evil.com\\@bank.com/x"))
    }

    @Test
    fun `urls without a host return null`() {
        assertNull(UrlUtils.hostOf("about:blank"))
        assertNull(UrlUtils.hostOf("data:text/html,x"))
        assertNull(UrlUtils.hostOf("https://"))
    }

    @Test
    fun `registrable domain understands indian suffixes`() {
        assertEquals("example.com", UrlUtils.registrableDomain("a.b.example.com"))
        assertEquals("example.co.in", UrlUtils.registrableDomain("news.example.co.in"))
        assertEquals("co.in", UrlUtils.registrableDomain("co.in"))
        assertEquals("localhost", UrlUtils.registrableDomain("localhost"))
        assertEquals("192.168.1.1", UrlUtils.registrableDomain("192.168.1.1"))
    }

    @Test
    fun `third party compares registrable domains`() {
        assertFalse(UrlUtils.isThirdParty("cdn.example.com", "www.example.com"))
        assertTrue(UrlUtils.isThirdParty("ads.net", "www.example.com"))
        assertTrue(UrlUtils.isThirdParty("a.co.in", "b.co.in"))
        assertFalse(UrlUtils.isThirdParty("ads.net", null))
    }

    @Test
    fun `youtube hosts are recognised`() {
        assertTrue(UrlUtils.isYouTube("m.youtube.com"))
        assertTrue(UrlUtils.isYouTube("youtu.be"))
        assertFalse(UrlUtils.isYouTube("notyoutube.com"))
        assertFalse(UrlUtils.isYouTube(null))
    }

    @Test
    fun `display url drops noise`() {
        assertEquals("example.com/a", UrlUtils.displayUrl("https://www.example.com/a/".removeSuffix("/")))
        assertEquals("example.com", UrlUtils.displayUrl("http://example.com/"))
    }
}
