package com.seasaw.theta.core.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNamesTest {
    /** Regression test: a right-to-left override made "invoice<U+202E>fdp.apk" display as "invoicekpa.pdf". */
    @Test
    fun `bidi and invisible characters cannot disguise the extension`() {
        assertEquals("invoicefdp.apk", DownloadNames.sanitize("invoice\u202Efdp.apk"))
        assertEquals("report.pdf.apk", DownloadNames.sanitize("report.pdf\u200B\u2066.apk"))
        assertEquals("a b.txt", DownloadNames.sanitize("a\u0000 b.txt"))
    }

    @Test
    fun `names cannot leave the downloads folder or hide themselves`() {
        assertEquals("passwd", DownloadNames.sanitize("../../etc/passwd"))
        assertEquals("evil.sh", DownloadNames.sanitize("..\\..\\evil.sh"))
        assertEquals("bashrc", DownloadNames.sanitize(".bashrc"))
        assertEquals("download", DownloadNames.sanitize(".."))
        assertEquals("download", DownloadNames.sanitize("\u202E"))
        assertEquals("download", DownloadNames.sanitize(null))
    }

    @Test
    fun `long names keep their extension in view`() {
        val name = DownloadNames.sanitize("a".repeat(300) + ".apk")
        assertEquals(120, name.length)
        assertTrue(name.endsWith(".apk"))
    }

    /** Regression test: cookies for any site a page pointed a download at were sent, and followed redirects. */
    @Test
    fun `cookies only go with downloads from the page's own site`() {
        assertTrue(DownloadNames.isSameSite("https://cdn.example.com/f.pdf", "https://www.example.com/page"))
        assertTrue(DownloadNames.isSameSite("https://files.example.co.in/f", "https://example.co.in/"))
        assertFalse(DownloadNames.isSameSite("https://bank.example/statement", "https://evil.example/"))
        assertFalse(DownloadNames.isSameSite("https://bank.example/statement", null))
        assertFalse(DownloadNames.isSameSite("https://a.co.in/f", "https://b.co.in/"))
    }
}
