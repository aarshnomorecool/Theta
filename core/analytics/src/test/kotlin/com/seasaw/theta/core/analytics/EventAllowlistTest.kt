package com.seasaw.theta.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventAllowlistTest {
    private class RecordingSink : AnalyticsSink {
        val sent = mutableListOf<String>()
        var enabled: Boolean? = null

        override fun send(name: String, params: Map<String, Any>) {
            sent += name
        }

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    @Test
    fun `declared events pass`() {
        assertTrue(EventAllowlist.permits("tab_opened", mapOf("incognito" to true)))
        assertTrue(EventAllowlist.permits("theme_changed", mapOf("mode" to "dark")))
    }

    @Test
    fun `unknown events and parameters are rejected`() {
        assertFalse(EventAllowlist.permits("page_view", emptyMap()))
        assertFalse(EventAllowlist.permits("tab_closed", mapOf("url" to "https://example.com")))
    }

    @Test
    fun `free text cannot be smuggled through a declared parameter`() {
        assertFalse(EventAllowlist.permits("theme_changed", mapOf("mode" to "https://example.com/secret?q=1")))
        assertFalse(EventAllowlist.permits("search_engine_changed", mapOf("engine" to "how to hide things")))
        assertFalse(EventAllowlist.permits("tab_opened", mapOf("incognito" to "true")))
    }

    @Test
    fun `tracker only forwards permitted events`() {
        val sink = RecordingSink()
        val analytics = AllowlistedAnalytics(sink)
        analytics.track(AnalyticsEvent.TabClosed)
        analytics.track(AnalyticsEvent.ThemeChanged("https://example.com"))
        assertEquals(listOf("tab_closed"), sink.sent)
    }

    @Test
    fun `every event in the sealed type is on the allowlist`() {
        val events = listOf(
            AnalyticsEvent.TabOpened(incognito = false),
            AnalyticsEvent.TabClosed,
            AnalyticsEvent.BookmarkAdded,
            AnalyticsEvent.DownloadStarted,
            AnalyticsEvent.FindInPageUsed,
            AnalyticsEvent.LocalDataCleared,
            AnalyticsEvent.DefaultBrowserPromptShown,
            AnalyticsEvent.AdBlockToggled(enabled = true),
            AnalyticsEvent.LiteModeToggled(enabled = true),
            AnalyticsEvent.DesktopModeToggled(enabled = true),
            AnalyticsEvent.ThemeChanged("system"),
            AnalyticsEvent.LanguageChanged("mr"),
            AnalyticsEvent.SearchEngineChanged("yandex"),
            AnalyticsEvent.FilterListUpdated("success"),
            AnalyticsEvent.AdShown("new_tab"),
        )
        events.forEach { assertTrue(it.name, EventAllowlist.permits(it.name, it.params)) }
    }

    @Test
    fun `consent is passed to the sink`() {
        val sink = RecordingSink()
        AllowlistedAnalytics(sink).setCollectionEnabled(false)
        assertEquals(false, sink.enabled)
    }
}
