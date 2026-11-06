package com.seasaw.theta.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabListRulesTest {
    private fun page(id: String, incognito: Boolean = false) =
        BrowserTab(id = id, url = "https://$id.example", isIncognito = incognito)

    @Test
    fun `closing a tab selects its right neighbour`() {
        val tabs = listOf(page("a"), page("b"), page("c"))
        assertEquals("c", TabListRules.nextSelection(tabs, "b"))
    }

    @Test
    fun `closing the last tab selects its left neighbour`() {
        val tabs = listOf(page("a"), page("b"))
        assertEquals("a", TabListRules.nextSelection(tabs, "b"))
    }

    @Test
    fun `selection stays in the same mode when it can`() {
        val tabs = listOf(page("a"), page("b", incognito = true), page("c"), page("d", incognito = true))
        assertEquals("d", TabListRules.nextSelection(tabs, "b"))
        assertEquals("c", TabListRules.nextSelection(tabs, "a"))
    }

    @Test
    fun `closing the only incognito tab falls back to a regular tab`() {
        val tabs = listOf(page("a"), page("b", incognito = true))
        assertEquals("a", TabListRules.nextSelection(tabs, "b"))
    }

    @Test
    fun `closing the only tab selects nothing`() {
        assertNull(TabListRules.nextSelection(listOf(page("a")), "a"))
    }

    @Test
    fun `an open new tab page is reused for its own mode only`() {
        val blank = BrowserTab("n")
        val tabs = listOf(page("a"), blank)
        assertEquals(blank, TabListRules.reusableNewTab(tabs, isIncognito = false))
        assertNull(TabListRules.reusableNewTab(tabs, isIncognito = true))
    }

    @Test
    fun `a new tab has no url and is not loading`() {
        val tab = BrowserTab("n")
        assertTrue(tab.isNewTab)
        assertFalse(tab.isLoading)
    }

    @Test
    fun `progress below done counts as loading`() {
        assertTrue(page("a").copy(progress = 50).isLoading)
        assertFalse(page("a").copy(progress = BrowserTab.PROGRESS_DONE).isLoading)
    }

    @Test
    fun `selected tab is looked up by id`() {
        val state = BrowserUiState(tabs = listOf(page("a"), page("b")), selectedTabId = "b")
        assertEquals("b", state.selectedTab?.id)
        assertNull(BrowserUiState().selectedTab)
    }
}
