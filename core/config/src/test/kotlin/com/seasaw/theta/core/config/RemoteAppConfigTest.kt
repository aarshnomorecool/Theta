package com.seasaw.theta.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAppConfigTest {
    private class FakeSource(private val overrides: Map<String, Any> = emptyMap()) : ConfigSource {
        private fun value(key: String): Any? = overrides[key] ?: ConfigDefaults.all[key]
        override fun boolean(key: String) = value(key) as Boolean
        override fun long(key: String) = value(key) as Long
        override fun string(key: String) = value(key) as String
        override suspend fun fetchAndActivate() = Unit
    }

    private fun config(vararg overrides: Pair<String, Any>) = RemoteAppConfig(FakeSource(mapOf(*overrides)))

    @Test
    fun `defaults allow ads and cap interstitials at four minutes`() {
        val config = config()
        assertTrue(config.adsEnabled)
        assertEquals(240L, config.interstitialMinIntervalSeconds)
    }

    @Test
    fun `kill switches are honoured`() {
        val config = config(ConfigKeys.ADS_ENABLED to false, ConfigKeys.ADBLOCK_ENABLED to false)
        assertFalse(config.adsEnabled)
        assertFalse(config.adBlockEnabled)
    }

    @Test
    fun `a broken remote interval cannot remove the frequency cap`() {
        assertEquals(240L, config(ConfigKeys.INTERSTITIAL_MIN_INTERVAL_SECONDS to 0L).interstitialMinIntervalSeconds)
        assertEquals(240L, config(ConfigKeys.INTERSTITIAL_MIN_INTERVAL_SECONDS to -5L).interstitialMinIntervalSeconds)
        assertEquals(600L, config(ConfigKeys.INTERSTITIAL_MIN_INTERVAL_SECONDS to 600L).interstitialMinIntervalSeconds)
    }

    @Test
    fun `filter list urls default to easylist easyprivacy and indianlist`() {
        assertEquals(
            listOf(
                "https://easylist.to/easylist/easylist.txt",
                "https://easylist.to/easylist/easyprivacy.txt",
                "https://easylist-downloads.adblockplus.org/indianlist.txt",
            ),
            config().filterListUrls,
        )
    }

    @Test
    fun `insecure or malformed filter list config falls back to defaults`() {
        val fallback = config().filterListUrls
        assertEquals(fallback, config(ConfigKeys.FILTER_LIST_URLS to "not json").filterListUrls)
        assertEquals(
            fallback,
            config(ConfigKeys.FILTER_LIST_URLS to """["http://evil.example/l.txt"]""").filterListUrls,
        )
    }

    @Test
    fun `insecure urls are filtered out of a mixed list`() {
        val mixed = """["http://evil.example/l.txt","https://good.example/l.txt"]"""
        assertEquals(listOf("https://good.example/l.txt"), config(ConfigKeys.FILTER_LIST_URLS to mixed).filterListUrls)
    }

    @Test
    fun `privacy policy url must be https`() {
        assertEquals("", config().privacyPolicyUrl)
        assertEquals("", config(ConfigKeys.PRIVACY_POLICY_URL to "http://x.example").privacyPolicyUrl)
        assertEquals("https://x.example", config(ConfigKeys.PRIVACY_POLICY_URL to "https://x.example").privacyPolicyUrl)
    }
}
