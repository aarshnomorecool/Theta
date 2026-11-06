package com.seasaw.theta.feature.adblock.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    private fun engine(vararg lines: String): FilterEngine {
        val compiler = FilterCompiler()
        lines.forEach(compiler::addLine)
        return FilterEngine(compiler.build())
    }

    private fun FilterEngine.blocks(
        url: String,
        page: String? = "https://site.example/",
        type: ResourceType = ResourceType.SCRIPT,
    ): Boolean {
        val host = url.substringAfter("://").substringBefore('/').substringBefore('?').lowercase()
        val pageHost = page?.substringAfter("://")?.substringBefore('/')?.lowercase()
        val isThirdParty = pageHost != null && siteOf(host) != siteOf(pageHost)
        return shouldBlock(url, host, page, pageHost, isThirdParty, type)
    }

    private fun siteOf(host: String) = host.split('.').takeLast(2).joinToString(".")

    @Test
    fun `plain domain rule blocks the domain and its subdomains only`() {
        val e = engine("||ads.example^")
        assertTrue(e.blocks("https://ads.example/x.js"))
        assertTrue(e.blocks("https://cdn.ads.example/x.js"))
        assertFalse(e.blocks("https://badads.example/x.js"))
        assertFalse(e.blocks("https://ads.example.org/x.js"))
    }

    @Test
    fun `third party domain rule ignores first party requests`() {
        val e = engine("||tracker.example^\$third-party")
        assertTrue(e.blocks("https://tracker.example/t.gif"))
        assertFalse(e.blocks("https://tracker.example/t.gif", page = "https://tracker.example/"))
    }

    @Test
    fun `host anchor matches at label boundaries`() {
        val e = engine("||ads.example/banner")
        assertTrue(e.blocks("https://ads.example/banner.gif"))
        assertTrue(e.blocks("https://x.ads.example/banner.gif"))
        assertFalse(e.blocks("https://xads.example/banner.gif"))
        assertFalse(e.blocks("https://other.example/?u=ads.example/banner"))
    }

    @Test
    fun `separator matches punctuation and the end of the url but not word characters`() {
        val e = engine("/banner^")
        assertTrue(e.blocks("https://a.example/banner"))
        assertTrue(e.blocks("https://a.example/banner/x"))
        assertTrue(e.blocks("https://a.example/banner?x"))
        assertFalse(e.blocks("https://a.example/banners"))
        assertFalse(e.blocks("https://a.example/banner-x"))
        assertFalse(e.blocks("https://a.example/banner.gif"))
    }

    @Test
    fun `wildcards span any text`() {
        val e = engine("/ads/*/banner.")
        assertTrue(e.blocks("https://a.example/ads/2024/06/banner.png"))
        assertFalse(e.blocks("https://a.example/ads/banner.png"))
    }

    @Test
    fun `backtracking finds later matches for a wildcard`() {
        val e = engine("/a/*/b/c")
        assertTrue(e.blocks("https://x.example/a/1/b/2/b/c"))
    }

    @Test
    fun `start and end anchors are enforced`() {
        val e = engine("|https://a.example/start", "/end.gif|")
        assertTrue(e.blocks("https://a.example/start/x"))
        assertFalse(e.blocks("https://b.example/?https://a.example/start"))
        assertTrue(e.blocks("https://b.example/end.gif"))
        assertFalse(e.blocks("https://b.example/end.gif?x=1"))
    }

    @Test
    fun `partial tokens are still found when the pattern is unanchored`() {
        val e = engine("adserver")
        assertTrue(e.blocks("https://a.example/myadserverfile.js"))
    }

    @Test
    fun `type options restrict the rule`() {
        val e = engine("/promo.\$image", "/track.\$~image")
        assertTrue(e.blocks("https://a.example/promo.png", type = ResourceType.IMAGE))
        assertFalse(e.blocks("https://a.example/promo.js", type = ResourceType.SCRIPT))
        assertFalse(e.blocks("https://a.example/track.png", type = ResourceType.IMAGE))
        assertTrue(e.blocks("https://a.example/track.js", type = ResourceType.SCRIPT))
    }

    @Test
    fun `first party option restricts the rule`() {
        val e = engine("/selfad.\$~third-party")
        assertTrue(e.blocks("https://site.example/selfad.js"))
        assertFalse(e.blocks("https://other.example/selfad.js"))
    }

    @Test
    fun `domain option limits rules to pages and most specific entry wins`() {
        val e = engine("/widget.\$domain=a.example|~sub.a.example")
        assertTrue(e.blocks("https://cdn.example/widget.js", page = "https://a.example/"))
        assertTrue(e.blocks("https://cdn.example/widget.js", page = "https://www.a.example/"))
        assertFalse(e.blocks("https://cdn.example/widget.js", page = "https://sub.a.example/"))
        assertFalse(e.blocks("https://cdn.example/widget.js", page = "https://b.example/"))
        assertFalse(e.blocks("https://cdn.example/widget.js", page = null))
    }

    @Test
    fun `excluded domain rule applies everywhere else`() {
        val e = engine("/widget.\$domain=~a.example")
        assertFalse(e.blocks("https://cdn.example/widget.js", page = "https://a.example/"))
        assertTrue(e.blocks("https://cdn.example/widget.js", page = "https://b.example/"))
    }

    @Test
    fun `exception rules override block rules`() {
        val e = engine("||ads.example^", "@@||ads.example/allowed.js")
        assertTrue(e.blocks("https://ads.example/blocked.js"))
        assertFalse(e.blocks("https://ads.example/allowed.js"))
    }

    @Test
    fun `important block rules override exceptions`() {
        val e = engine("/force.\$important", "@@/force.")
        assertTrue(e.blocks("https://a.example/force.js"))
    }

    @Test
    fun `document exception turns blocking off for a page`() {
        val e = engine("||ads.example^", "@@||trusted.example^\$document")
        assertFalse(e.blocks("https://ads.example/x.js", page = "https://trusted.example/"))
        assertTrue(e.blocks("https://ads.example/x.js", page = "https://site.example/"))
    }

    @Test
    fun `match case is honoured`() {
        val e = engine("/AdBanner.\$match-case")
        assertTrue(e.blocks("https://a.example/AdBanner.js"))
        assertFalse(e.blocks("https://a.example/adbanner.js"))
    }

    @Test
    fun `matching ignores case by default`() {
        assertTrue(engine("/banner.").blocks("https://A.example/BANNER.js"))
    }

    @Test
    fun `main frame navigations are never blocked`() {
        assertFalse(engine("||ads.example^").blocks("https://ads.example/", type = ResourceType.DOCUMENT))
    }

    @Test
    fun `cosmetic rules comments and unsupported rules are ignored`() {
        val e = engine(
            "! comment",
            "[Adblock Plus 2.0]",
            "example.com##.ad",
            "example.com#@#.ad",
            "example.com#?#.ad:-abp-has(x)",
            "/regex-rule/",
            "/ok.\$csp=script-src 'none'",
            "/popup-only.\$popup",
            "/unknown.\$somefutureoption",
            "/entity.\$domain=example.*",
        )
        assertEquals(0, e.ruleCount)
    }

    @Test
    fun `too short and match everything rules are dropped`() {
        assertEquals(0, engine("*", "^", "ab", "||", "*\$script").ruleCount)
    }

    @Test
    fun `non ascii rules are dropped`() {
        assertEquals(0, engine("/बैनर/x.js").ruleCount)
    }

    @Test
    fun `dollar inside a pattern is not an option separator`() {
        val e = engine("/price\$10/x")
        assertTrue(e.blocks("https://a.example/price\$10/x"))
    }

    @Test
    fun `subdomain rules are absorbed by a parent domain rule`() {
        val e = engine("||sub.ads.example^", "||ads.example^")
        assertTrue(e.blocks("https://ads.example/"))
        assertTrue(e.blocks("https://other.sub.ads.example/"))
    }

    @Test
    fun `duplicate lines count once`() {
        assertEquals(1, engine("/banner.", "/banner.").ruleCount)
    }

    @Test
    fun `very long urls do not break matching`() {
        val e = engine("||ads.example^", "/banner.")
        val url = "https://clean.example/" + "a".repeat(100_000)
        assertFalse(e.blocks(url))
        assertTrue(e.blocks("https://ads.example/" + "a".repeat(100_000)))
    }

    @Test
    fun `snapshot survives a round trip`() {
        val compiler = FilterCompiler()
        listOf("||ads.example^", "||t.example^\$third-party", "/banner.\$domain=a.example", "@@/banner.ok").forEach(
            compiler::addLine,
        )
        val bytes = ByteArrayOutputStream().also { SnapshotIo.write(compiler.build(), it) }.toByteArray()
        val e = FilterEngine(SnapshotIo.read(ByteArrayInputStream(bytes)))

        assertEquals(4, e.ruleCount)
        assertTrue(e.blocks("https://ads.example/x"))
        assertTrue(e.blocks("https://t.example/x"))
        assertTrue(e.blocks("https://cdn.example/banner.js", page = "https://a.example/"))
        assertFalse(e.blocks("https://cdn.example/banner.ok", page = "https://a.example/"))
    }

    @Test(expected = IOException::class)
    fun `garbage is rejected`() {
        SnapshotIo.read(ByteArrayInputStream(ByteArray(64) { 7 }))
    }

    @Test(expected = IOException::class)
    fun `truncated snapshots are rejected`() {
        val compiler = FilterCompiler().apply { addLine("||ads.example^") }
        val bytes = ByteArrayOutputStream().also { SnapshotIo.write(compiler.build(), it) }.toByteArray()
        SnapshotIo.read(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)))
    }
}
