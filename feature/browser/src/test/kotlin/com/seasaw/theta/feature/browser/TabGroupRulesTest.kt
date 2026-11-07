package com.seasaw.theta.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class TabGroupRulesTest {
    private fun tabs(vararg ids: String) = ids.map { BrowserTab(it, "https://$it.example") }

    @Test
    fun `new groups take the first unused colour`() {
        val groups = listOf(TabGroup("a", "", 0), TabGroup("b", "", 1))
        assertEquals(2, TabGroupRules.nextColor(groups))
        assertEquals(0, TabGroupRules.nextColor(emptyList()))
    }

    @Test
    fun `grouped tabs move next to each other`() {
        val group = TabGroup("g", "Work", 0)
        val moved = TabGroupRules.moveIntoGroup(tabs("a", "b", "c", "d"), setOf("b", "d"), group)
        assertEquals(listOf("a", "b", "d", "c"), moved.map { it.id })
        assertEquals(listOf(null, "g", "g", null), moved.map { it.groupId })
    }

    @Test
    fun `tabs joining a group go after its last tab`() {
        val group = TabGroup("g", "Work", 0)
        val start = tabs("a", "b", "c").mapIndexed { i, tab -> if (i == 0) tab.copy(groupId = "g") else tab }
        val moved = TabGroupRules.moveIntoGroup(start, setOf("c"), group)
        assertEquals(listOf("a", "c", "b"), moved.map { it.id })
    }

    @Test
    fun `private tabs never join a regular group`() {
        val group = TabGroup("g", "Work", 0, isIncognito = false)
        val start = listOf(BrowserTab("p", "https://p.example", isIncognito = true))
        assertEquals(start, TabGroupRules.moveIntoGroup(start, setOf("p"), group))
    }

    @Test
    fun `empty groups are dropped`() {
        val groups = listOf(TabGroup("g", "", 0), TabGroup("h", "", 1))
        val open = listOf(BrowserTab("a", groupId = "h"))
        assertEquals(listOf("h"), TabGroupRules.prune(open, groups).map { it.id })
    }

    @Test
    fun `a new tab in a group opens beside the group`() {
        val open =
            listOf(BrowserTab("a", groupId = "g"), BrowserTab("b"), BrowserTab("c", groupId = "g"), BrowserTab("d"))
        assertEquals(3, TabGroupRules.insertionIndex(open, "g"))
        assertEquals(4, TabGroupRules.insertionIndex(open, null))
    }
}
