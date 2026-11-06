package com.seasaw.theta.core.common.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressBarParserTest {
    private fun url(value: String) = AddressInput.Url(value)
    private fun search(value: String) = AddressInput.Search(value)

    @Test
    fun `blank input is nothing`() {
        assertNull(AddressBarParser.parse("   "))
    }

    @Test
    fun `bare domains default to https`() {
        assertEquals(url("https://example.com"), AddressBarParser.parse("example.com"))
        assertEquals(url("https://news.example.co.in/a?b=c#d"), AddressBarParser.parse("news.example.co.in/a?b=c#d"))
    }

    @Test
    fun `explicit schemes are kept and hosts lowercased`() {
        assertEquals(url("http://example.com/Path"), AddressBarParser.parse("HTTP://Example.COM/Path"))
    }

    @Test
    fun `local addresses default to http`() {
        assertEquals(url("http://localhost:8080/x"), AddressBarParser.parse("localhost:8080/x"))
        assertEquals(url("http://192.168.1.1"), AddressBarParser.parse("192.168.1.1"))
        assertEquals(url("http://[::1]:3000/"), AddressBarParser.parse("[::1]:3000/"))
    }

    @Test
    fun `host and port is not mistaken for a scheme`() {
        assertEquals(url("https://example.com:8443"), AddressBarParser.parse("example.com:8443"))
    }

    @Test
    fun `unicode domains become punycode`() {
        assertEquals(url("https://xn--h2brj9c.xn--h2brj9c"), AddressBarParser.parse("भारत.भारत"))
    }

    @Test
    fun `text with spaces is a search`() {
        assertEquals(search("best phone under 10000"), AddressBarParser.parse("  best phone under 10000 "))
        assertEquals(search("example.com is down"), AddressBarParser.parse("example.com is down"))
    }

    @Test
    fun `single words and hindi queries are searches`() {
        assertEquals(search("weather"), AddressBarParser.parse("weather"))
        assertEquals(search("मौसम"), AddressBarParser.parse("मौसम"))
    }

    @Test
    fun `file-like names are searches unless clearly a url`() {
        assertEquals(search("node.js"), AddressBarParser.parse("node.js"))
        assertEquals(url("https://node.js/docs"), AddressBarParser.parse("node.js/docs"))
        assertEquals(url("https://node.js"), AddressBarParser.parse("https://node.js"))
    }

    @Test
    fun `dangerous schemes never navigate`() {
        listOf(
            "javascript:alert(1)",
            "file:///data/data/x",
            "content://x/y",
            "data:text/html,hi",
            "intent://x#Intent;end",
        ).forEach { assertEquals(it, search(it), AddressBarParser.parse(it)) }
    }

    @Test
    fun `credentials are stripped from typed urls`() {
        assertEquals(url("https://evil.com/x"), AddressBarParser.parse("https://bank.com@evil.com/x"))
        assertEquals(url("https://evil.com/x"), AddressBarParser.parse("https://evil.com@evil.com/x"))
    }

    @Test
    fun `invalid hosts and ports are searches`() {
        listOf("a..b", "-a.com", "a-.com", "999.1.1.1", "example.c", "example.com:99999").forEach {
            assertEquals(it, search(it), AddressBarParser.parse(it))
        }
    }

    @Test
    fun `resolve encodes searches with the chosen engine`() {
        assertEquals(
            "https://duckduckgo.com/?q=a+%26+b",
            AddressBarParser.resolve("a & b", SearchEngine.DUCKDUCKGO),
        )
        assertEquals("https://example.com", AddressBarParser.resolve("example.com", SearchEngine.GOOGLE))
    }

    @Test
    fun `about blank is allowed`() {
        assertEquals(url("about:blank"), AddressBarParser.parse("ABOUT:blank"))
    }
}
