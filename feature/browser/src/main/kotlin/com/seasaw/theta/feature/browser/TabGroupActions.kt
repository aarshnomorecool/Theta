package com.seasaw.theta.feature.browser

import java.util.UUID

/**
 * Tab group handling for [BrowserViewModel]. Kept apart so the view model stays about tabs and pages; everything here
 * is plain state edits plus the two tab operations groups need.
 */
internal class TabGroupActions(
    private val state: () -> BrowserUiState,
    private val update: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val openNewTab: (isIncognito: Boolean, groupId: String?) -> Unit,
    private val closeTabs: (Set<String>) -> Unit,
    private val save: () -> Unit,
) {
    fun create(tabIds: Set<String>, name: String, colorIndex: Int, isIncognito: Boolean) {
        val group = TabGroup(UUID.randomUUID().toString(), name.trim(), colorIndex, isIncognito)
        if (tabIds.isEmpty()) {
            update { it.copy(groups = it.groups + group) }
            openNewTab(isIncognito, group.id)
            return
        }
        update { current ->
            val tabs = TabGroupRules.moveIntoGroup(current.tabs, tabIds, group)
            current.copy(tabs = tabs, groups = TabGroupRules.prune(tabs, current.groups + group))
        }
        save()
    }

    fun addTo(tabIds: Set<String>, groupId: String) {
        val group = state().group(groupId) ?: return
        update { current ->
            val tabs = TabGroupRules.moveIntoGroup(current.tabs, tabIds, group)
            current.copy(tabs = tabs, groups = TabGroupRules.prune(tabs, current.groups))
        }
        save()
    }

    fun remove(tabIds: Set<String>) {
        update { current ->
            val tabs = current.tabs.map { if (it.id in tabIds) it.copy(groupId = null) else it }
            current.copy(tabs = tabs, groups = TabGroupRules.prune(tabs, current.groups))
        }
        save()
    }

    fun edit(id: String, name: String, colorIndex: Int) {
        update { current ->
            current.copy(
                groups = current.groups.map {
                    if (it.id == id) it.copy(name = name.trim(), colorIndex = colorIndex) else it
                },
            )
        }
        save()
    }

    fun ungroup(id: String) = remove(state().tabs.filter { it.groupId == id }.map { it.id }.toSet())

    fun close(id: String) = closeTabs(state().tabs.filter { it.groupId == id }.map { it.id }.toSet())

    /**
     * "Open in new tab in group": joins the current tab's group, or starts a new group holding both tabs, as other
     * browsers do. The new group has no name; the switcher then labels it by its tab count. Returns its id.
     */
    fun groupForLinkFrom(source: BrowserTab): String {
        source.groupId?.let { return it }
        val current = state()
        val group = TabGroup(
            id = UUID.randomUUID().toString(),
            name = "",
            colorIndex = TabGroupRules.nextColor(current.groups),
            isIncognito = source.isIncognito,
        )
        update {
            it.copy(
                tabs = TabGroupRules.moveIntoGroup(it.tabs, setOf(source.id), group),
                groups =
                    it.groups + group,
            )
        }
        return group.id
    }
}
