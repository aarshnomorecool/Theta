package com.seasaw.theta.core.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebviewPolicyTest {
    @Test
    fun `web schemes load in the webview`() {
        listOf("https://a.example", "http://a.example", "about:blank", "blob:https://a.example/1").forEach {
            assertEquals(it, NavigationDecision.LoadInWebView, NavigationPolicy.decide(it))
        }
    }

    @Test
    fun `dangerous schemes are blocked`() {
        listOf(
            "file:///data/data/x",
            "content://x/y",
            "javascript:alert(1)",
            "JAVASCRIPT:alert(1)",
            "data:text/html,hi",
            "chrome://settings",
            "no-scheme-here",
        ).forEach { assertEquals(it, NavigationDecision.Block, NavigationPolicy.decide(it)) }
    }

    @Test
    fun `other apps always go through the user prompt`() {
        listOf(
            "intent://x#Intent;scheme=y;end",
            "market://details?id=a",
            "upi://pay?pa=a",
            "tel:123",
            "mailto:a@b",
            "whatsapp://send",
        ).forEach { assertEquals(it, NavigationDecision.AskToOpenExternally, NavigationPolicy.decide(it)) }
    }

    @Test
    fun `tracker keeps the three most recent tabs`() {
        val tracker = LiveTabTracker(maxLive = 3)
        assertTrue(tracker.touch("a").isEmpty())
        assertTrue(tracker.touch("b").isEmpty())
        assertTrue(tracker.touch("c").isEmpty())
        assertEquals(listOf("a"), tracker.touch("d"))
    }

    @Test
    fun `touching a tab protects it from eviction`() {
        val tracker = LiveTabTracker(maxLive = 3)
        listOf("a", "b", "c").forEach { tracker.touch(it) }
        tracker.touch("a")
        assertEquals(listOf("b"), tracker.touch("d"))
    }

    @Test
    fun `removed tabs free a slot`() {
        val tracker = LiveTabTracker(maxLive = 2)
        tracker.touch("a")
        tracker.touch("b")
        tracker.remove("a")
        assertTrue(tracker.touch("c").isEmpty())
    }

    @Test
    fun `trim keeps only the visible tab`() {
        val tracker = LiveTabTracker(maxLive = 3)
        listOf("a", "b", "c").forEach { tracker.touch(it) }
        assertEquals(listOf("a", "c"), tracker.allExcept("b"))
    }

    @Test
    fun `desktop user agent drops mobile markers and keeps the engine version`() {
        val mobile = "Mozilla/5.0 (Linux; Android 14; Pixel 8a; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/130.0.0.0 Mobile Safari/537.36"
        val desktop = UserAgents.desktop(mobile)
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
        assertFalse(desktop.contains("wv"))
        assertTrue(desktop.contains("Chrome/130.0.0.0"))
        assertTrue(desktop.contains("X11; Linux x86_64"))
    }

    @Test
    fun `resource kinds prefer fetch metadata then accept then extension`() {
        val url = "https://a.example/x.js"
        assertEquals(ResourceKind.DOCUMENT, ResourceClassifier.classify(url, true, null, null))
        assertEquals(ResourceKind.IMAGE, ResourceClassifier.classify(url, false, "text/css", "image"))
        assertEquals(ResourceKind.STYLESHEET, ResourceClassifier.classify(url, false, "text/css,*/*", null))
        assertEquals(ResourceKind.SCRIPT, ResourceClassifier.classify(url, false, "*/*", null))
    }

    @Test
    fun `extension classification ignores queries and fragments`() {
        fun kindOf(url: String) = ResourceClassifier.classify(url, false, "*/*", null)
        assertEquals(ResourceKind.IMAGE, kindOf("https://a.example/p.PNG?v=1"))
        assertEquals(ResourceKind.MEDIA, kindOf("https://a.example/seg.m3u8#t"))
        assertEquals(ResourceKind.FONT, kindOf("https://a.example/f.woff2"))
        assertEquals(ResourceKind.OTHER, kindOf("https://a.example/"))
        assertEquals(ResourceKind.OTHER, kindOf("https://a.example.js/path"))
    }
}
