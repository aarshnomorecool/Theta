package com.seasaw.theta.feature.adblock.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticRulesTest {
    private fun rules(vararg lines: String) = CosmeticCompiler().apply { lines.forEach(::addLine) }.build()

    @Test
    fun `generic id and class rules apply only when the page uses them`() {
        val rules = rules("###cookie-bar", "##.consent-box")
        assertEquals(
            listOf("#cookie-bar"),
            rules.selectorsFor("news.example", listOf("cookie-bar", "main"), emptyList()),
        )
        assertEquals(emptyList<String>(), rules.selectorsFor("news.example", listOf("main"), listOf("article")))
        assertEquals(listOf(".consent-box"), rules.selectorsFor("news.example", emptyList(), listOf("consent-box")))
    }

    @Test
    fun `site rules apply to the site and its subdomains only`() {
        val rules = rules("example.com##div.notice")
        assertEquals(listOf("div.notice"), rules.selectorsFor("m.example.com", emptyList(), emptyList()))
        assertEquals(listOf("div.notice"), rules.selectorsFor("www.example.com", emptyList(), emptyList()))
        assertTrue(rules.selectorsFor("other.com", emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `a site can switch a generic rule off`() {
        val rules = rules("###cookie-bar", "shop.example#@##cookie-bar", "~news.example###cookie-bar")
        assertTrue(rules.selectorsFor("shop.example", listOf("cookie-bar"), emptyList()).isEmpty())
        assertTrue(rules.selectorsFor("news.example", listOf("cookie-bar"), emptyList()).isEmpty())
        assertEquals(listOf("#cookie-bar"), rules.selectorsFor("other.example", listOf("cookie-bar"), emptyList()))
    }

    @Test
    fun `a generic exception removes the rule everywhere`() {
        assertTrue(rules("##.banner", "#@#.banner").selectorsFor("a.example", emptyList(), listOf("banner")).isEmpty())
    }

    @Test
    fun `complex generic rules always apply`() {
        val rules = rules("##div[id^=\"cookie\"]")
        assertEquals(listOf("div[id^=\"cookie\"]"), rules.selectorsFor("a.example", emptyList(), emptyList()))
    }

    /** A selector is placed inside a stylesheet, so none may close the rule and add CSS of its own. */
    @Test
    fun `selectors that could escape the stylesheet are dropped`() {
        val rules = rules(
            "##.a{}body{display:none",
            "##.b</style><script>",
            "##.c /* x */",
            "##@import url(x)",
            "example.com##.d:has-text(Accept)",
            "example.com#?#.e:-abp-has(.f)",
            "example.com##+js(set-cookie)",
        )
        assertEquals(0, rules.ruleCount)
    }

    @Test
    fun `network lines are left to the network compiler`() {
        assertEquals(0, rules("||consent.example^\$third-party", "-cookie-banner.").ruleCount)
    }

    @Test
    fun `a pack survives writing and reading back`() {
        val compiler = CookiePackCompiler()
        listOf("||consentmanager.example^", "###cookie-bar", "##.consent", "example.com##.notice", "a.com#@#.consent")
            .forEach(compiler::addLine)
        val pack = compiler.build()
        val bytes = ByteArrayOutputStream().also { CookiePackIo.write(pack, it) }.toByteArray()
        val read = CookiePackIo.read(ByteArrayInputStream(bytes))

        assertEquals(pack.ruleCount, read.ruleCount)
        assertEquals(
            listOf(".notice", "#cookie-bar", ".consent"),
            read.cosmetic.selectorsFor("example.com", listOf("cookie-bar"), listOf("consent")),
        )
        assertFalse(read.cosmetic.selectorsFor("a.com", emptyList(), listOf("consent")).contains(".consent"))
        val engine = FilterEngine(read.network)
        assertTrue(
            engine.shouldBlock(
                url = "https://consentmanager.example/cmp.js",
                host = "consentmanager.example",
                pageUrl = "https://news.example/",
                pageHost = "news.example",
                isThirdParty = true,
                type = ResourceType.SCRIPT,
            ),
        )
    }

    @Test
    fun `domains include every parent`() {
        assertEquals(
            listOf("a.b.example.com", "b.example.com", "example.com", "com"),
            CosmeticRules.domainsOf("a.b.example.com"),
        )
    }
}
