package com.seasaw.theta.feature.browser

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.ui.ConfirmDialog
import com.seasaw.theta.core.ui.SiteIcon
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme

internal object TabSwitcherTestTags {
    const val CARD_PREFIX = "tab_card_"
    const val CLOSE_PREFIX = "tab_close_"
    const val GROUP_PREFIX = "group_card_"
    const val NEW_TAB = "switcher_new_tab"
    const val INCOGNITO_SECTION = "switcher_incognito"
    const val REGULAR_SECTION = "switcher_regular"
    const val GROUPS_SECTION = "switcher_groups"
    const val MENU = "switcher_menu"
    const val SELECTION_GROUP = "selection_group"
    const val SELECTION_CLOSE = "selection_close"
}

internal enum class SwitcherPage { TABS, PRIVATE, GROUPS }

/** Where the switcher's menu leads outside the browser. */
@Immutable
internal data class SwitcherNavigation(
    val onDismiss: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onClearBrowsingData: () -> Unit = {},
)

private sealed interface SwitcherDialog {
    data class GroupTabs(val tabIds: Set<String>, val isIncognito: Boolean) : SwitcherDialog
    data class NewGroup(val isIncognito: Boolean) : SwitcherDialog
    data class EditGroup(val group: TabGroup) : SwitcherDialog
    data class CloseAll(val isIncognito: Boolean, val count: Int) : SwitcherDialog
}

/** One card in the grid: a tab, or a group standing in for all of its tabs. */
private sealed interface SwitcherEntry {
    val key: String

    data class Tab(val tab: BrowserTab) : SwitcherEntry {
        override val key: String get() = tab.id
    }

    data class Group(val group: TabGroup, val tabs: List<BrowserTab>) : SwitcherEntry {
        override val key: String get() = "group_" + group.id
    }
}

/** Grouped tabs collapse into one card where the group's first tab sits. */
private fun entriesFor(tabs: List<BrowserTab>, groups: List<TabGroup>): List<SwitcherEntry> {
    val seenGroups = mutableSetOf<String>()
    return tabs.mapNotNull { tab ->
        val group = groups.firstOrNull { it.id == tab.groupId }
        when {
            group == null -> SwitcherEntry.Tab(tab)
            seenGroups.add(group.id) -> SwitcherEntry.Group(group, tabs.filter { it.groupId == group.id })
            else -> null
        }
    }
}

/**
 * The tab switcher: a grid of page previews, with groups shown as one card each.
 *
 * It carries no ad. Tab previews are pictures of web content, and ads rule 1 keeps ads away from web content.
 */
@Composable
internal fun TabSwitcher(
    state: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    navigation: SwitcherNavigation,
    modifier: Modifier = Modifier,
) {
    val startsPrivate = state.selectedTab?.isIncognito == true
    var page by rememberSaveable { mutableStateOf(if (startsPrivate) SwitcherPage.PRIVATE else SwitcherPage.TABS) }
    var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    var dialog by remember { mutableStateOf<SwitcherDialog?>(null) }

    val openGroup = state.group(openGroupId)
    BackHandler(enabled = selection != null || openGroup != null) {
        if (selection != null) selection = null else openGroupId = null
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        if (openGroup != null) {
            GroupDetail(
                group = openGroup,
                state = state,
                onEvent = onEvent,
                onBack = { openGroupId = null },
                onEdit = { dialog = SwitcherDialog.EditGroup(openGroup) },
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        val isPrivate = page == SwitcherPage.PRIVATE
        val pageTabs = state.tabs.filter { it.isIncognito == isPrivate }
        val currentSelection = selection
        if (currentSelection != null) {
            SelectionBar(
                count = currentSelection.size,
                canUngroup = pageTabs.any { it.id in currentSelection && it.groupId != null },
                onCancel = { selection = null },
                onSelectAll = { selection = pageTabs.map { it.id }.toSet() },
                onGroup = { dialog = SwitcherDialog.GroupTabs(currentSelection, isPrivate) },
                onUngroup = {
                    onEvent(BrowserUiEvent.RemoveFromGroup(currentSelection))
                    selection = null
                },
                onClose = {
                    onEvent(BrowserUiEvent.CloseTabs(currentSelection))
                    selection = null
                },
            )
        } else {
            SwitcherTopBar(
                page = page,
                regularCount = state.tabs.count { !it.isIncognito },
                privateCount = state.tabs.count { it.isIncognito },
                onPage = { page = it },
                menu = { onDismissMenu ->
                    SwitcherMenuItems(
                        onNewTab = { onEvent(BrowserUiEvent.NewTab(isIncognito = false)) },
                        onNewPrivateTab = { onEvent(BrowserUiEvent.NewTab(isIncognito = true)) },
                        onNewGroup = { dialog = SwitcherDialog.NewGroup(isPrivate) },
                        onCloseAll = {
                            val count = state.tabs.count { it.isIncognito == isPrivate }
                            if (count > 0) dialog = SwitcherDialog.CloseAll(isPrivate, count)
                        },
                        onSelect = {
                            if (page == SwitcherPage.GROUPS) page = SwitcherPage.TABS
                            selection = emptySet()
                        },
                        onClearData = navigation.onClearBrowsingData,
                        onSettings = navigation.onSettings,
                        isPrivatePage = isPrivate,
                        onDismiss = onDismissMenu,
                    )
                },
            )
        }

        Box(Modifier.weight(1f)) {
            when (page) {
                SwitcherPage.GROUPS -> GroupsPage(state, onOpen = { openGroupId = it })

                else -> TabsPage(
                    entries = entriesFor(pageTabs, state.groups),
                    state = state,
                    isPrivate = isPrivate,
                    selection = currentSelection,
                    onSelectionChange = { selection = it },
                    onEvent = onEvent,
                    onOpenGroup = { openGroupId = it },
                )
            }
        }

        if (currentSelection == null) {
            SwitcherBottomBar(
                onAdd = {
                    when (page) {
                        SwitcherPage.GROUPS -> dialog = SwitcherDialog.NewGroup(isIncognito = false)
                        else -> onEvent(BrowserUiEvent.NewTab(isIncognito = isPrivate))
                    }
                },
                addLabel = stringResource(
                    if (page ==
                        SwitcherPage.GROUPS
                    ) {
                        R.string.group_new_title
                    } else {
                        R.string.new_tab
                    },
                ),
                onDone = navigation.onDismiss,
            )
        }
    }

    SwitcherDialogs(dialog, state, onEvent) { closedFlow ->
        dialog = null
        if (closedFlow) selection = null
    }
}

@Composable
private fun SwitcherDialogs(
    dialog: SwitcherDialog?,
    state: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    onDone: (endSelection: Boolean) -> Unit,
) {
    when (dialog) {
        is SwitcherDialog.GroupTabs -> GroupTabsFlow(dialog.tabIds, dialog.isIncognito, state, onEvent) { onDone(true) }

        is SwitcherDialog.NewGroup -> GroupEditorDialog(
            title = stringResource(R.string.group_new_title),
            confirmLabel = stringResource(R.string.action_create),
            initialName = "",
            initialColor = TabGroupRules.nextColor(state.groups),
            onConfirm = { name, color ->
                onEvent(BrowserUiEvent.CreateGroup(emptySet(), name, color, dialog.isIncognito))
                onDone(false)
            },
            onDismiss = { onDone(false) },
        )

        is SwitcherDialog.EditGroup -> GroupEditorDialog(
            title = stringResource(R.string.group_edit_title),
            confirmLabel = stringResource(R.string.action_save),
            initialName = dialog.group.name,
            initialColor = dialog.group.colorIndex,
            onConfirm = { name, color ->
                onEvent(BrowserUiEvent.EditGroup(dialog.group.id, name, color))
                onDone(false)
            },
            onDismiss = { onDone(false) },
        )

        is SwitcherDialog.CloseAll -> ConfirmDialog(
            title = pluralStringResource(
                if (dialog.isIncognito) R.plurals.close_all_private_title else R.plurals.close_all_title,
                dialog.count,
                dialog.count,
            ),
            message = stringResource(R.string.close_all_message),
            confirmLabel = stringResource(R.string.action_close_all),
            onConfirm = {
                onEvent(BrowserUiEvent.CloseAllTabs(dialog.isIncognito))
                onDone(false)
            },
            onDismiss = { onDone(false) },
        )

        null -> Unit
    }
}

@Composable
private fun SwitcherTopBar(
    page: SwitcherPage,
    regularCount: Int,
    privateCount: Int,
    onPage: (SwitcherPage) -> Unit,
    menu: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Labelled chips rather than bare icons: "4 open tabs" says more than a number in a box. They scroll
        // sideways when a long translation does not fit.
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PageChip(
                isSelected = page == SwitcherPage.TABS,
                label = pluralStringResource(R.plurals.switcher_tabs_label, regularCount, regularCount),
                icon = ThetaIcons.Tabs,
                onClick = { onPage(SwitcherPage.TABS) },
                modifier = Modifier.testTag(TabSwitcherTestTags.REGULAR_SECTION),
            )
            PageChip(
                isSelected = page == SwitcherPage.PRIVATE,
                label = pluralStringResource(R.plurals.switcher_private_label, privateCount, privateCount),
                icon = ThetaIcons.Private,
                onClick = { onPage(SwitcherPage.PRIVATE) },
                modifier = Modifier.testTag(TabSwitcherTestTags.INCOGNITO_SECTION),
            )
            PageChip(
                isSelected = page == SwitcherPage.GROUPS,
                label = stringResource(R.string.switcher_groups),
                icon = ThetaIcons.Groups,
                onClick = { onPage(SwitcherPage.GROUPS) },
                modifier = Modifier.testTag(TabSwitcherTestTags.GROUPS_SECTION),
            )
        }
        Box {
            IconButton(onClick = { isMenuOpen = true }, Modifier.testTag(TabSwitcherTestTags.MENU)) {
                Icon(ThetaIcons.More, stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                menu { isMenuOpen = false }
            }
        }
    }
}

@Composable
private fun PageChip(
    isSelected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.primaryContainer else colors.surfaceContainerHigh)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SwitcherMenuItems(
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onNewGroup: () -> Unit,
    onCloseAll: () -> Unit,
    onSelect: () -> Unit,
    onClearData: () -> Unit,
    onSettings: () -> Unit,
    isPrivatePage: Boolean,
    onDismiss: () -> Unit,
) {
    @Composable
    fun item(label: Int, icon: ImageVector, action: () -> Unit) = DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        onClick = {
            onDismiss()
            action()
        },
    )
    item(R.string.new_tab, ThetaIcons.Plus, onNewTab)
    item(R.string.menu_new_incognito, ThetaIcons.NewPrivateTab, onNewPrivateTab)
    item(R.string.group_new_title, ThetaIcons.NewGroup, onNewGroup)
    HorizontalDivider()
    item(
        if (isPrivatePage) R.string.tabs_close_incognito else R.string.switcher_close_all,
        ThetaIcons.Close,
        onCloseAll,
    )
    item(R.string.switcher_select, ThetaIcons.Select, onSelect)
    HorizontalDivider()
    item(R.string.switcher_clear_data, ThetaIcons.Trash, onClearData)
    item(R.string.menu_settings, ThetaIcons.Settings, onSettings)
}

@Composable
private fun SelectionBar(
    count: Int,
    canUngroup: Boolean,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onGroup: () -> Unit,
    onUngroup: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) { Icon(ThetaIcons.Close, stringResource(R.string.selection_cancel)) }
        Text(
            if (count ==
                0
            ) {
                stringResource(R.string.switcher_select)
            } else {
                stringResource(R.string.selection_count, count)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll) { Text(stringResource(R.string.selection_all)) }
        IconButton(
            onClick = onGroup,
            enabled = count > 0,
            modifier = Modifier.testTag(TabSwitcherTestTags.SELECTION_GROUP),
        ) {
            Icon(ThetaIcons.NewGroup, stringResource(R.string.selection_group))
        }
        if (canUngroup) {
            IconButton(onClick = onUngroup) { Icon(ThetaIcons.Ungroup, stringResource(R.string.selection_ungroup)) }
        }
        IconButton(
            onClick = onClose,
            enabled = count > 0,
            modifier = Modifier.testTag(TabSwitcherTestTags.SELECTION_CLOSE),
        ) {
            Icon(ThetaIcons.Trash, stringResource(R.string.selection_close))
        }
    }
}

/** A labelled "New tab" button where the thumb rests, and a way back to the page. */
@Composable
private fun SwitcherBottomBar(onAdd: () -> Unit, addLabel: String, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onAdd,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .height(48.dp)
                .testTag(TabSwitcherTestTags.NEW_TAB),
        ) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ThetaIcons.Plus, null, Modifier.size(22.dp))
                Text(
                    addLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDone) {
            Text(stringResource(R.string.switcher_done), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TabsPage(
    entries: List<SwitcherEntry>,
    state: BrowserUiState,
    isPrivate: Boolean,
    selection: Set<String>?,
    onSelectionChange: (Set<String>) -> Unit,
    onEvent: (BrowserUiEvent) -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    if (entries.isEmpty()) {
        SwitcherEmpty(
            icon = if (isPrivate) ThetaIcons.Private else ThetaIcons.Tabs,
            title = stringResource(if (isPrivate) R.string.tabs_private_empty_title else R.string.tabs_none),
            message = stringResource(if (isPrivate) R.string.tabs_no_incognito else R.string.tabs_none_message),
        )
        return
    }
    SwitcherGrid {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is SwitcherEntry.Tab -> {
                    val tab = entry.tab
                    TabCard(
                        tab = tab,
                        preview = state.previews[tab.id],
                        isCurrent = tab.id == state.selectedTabId,
                        isChecked = selection?.let { tab.id in it },
                        onClick = {
                            if (selection != null) {
                                onSelectionChange(selection.toggle(setOf(tab.id)))
                            } else {
                                onEvent(BrowserUiEvent.SelectTab(tab.id))
                            }
                        },
                        onLongClick = { onSelectionChange((selection ?: emptySet()) + tab.id) },
                        onClose = { onEvent(BrowserUiEvent.CloseTab(tab.id)) },
                    )
                }

                is SwitcherEntry.Group -> {
                    val ids = entry.tabs.map { it.id }.toSet()
                    GroupCard(
                        group = entry.group,
                        tabs = entry.tabs,
                        previews = state.previews,
                        isChecked = selection?.let { it.containsAll(ids) },
                        onClick = {
                            if (selection !=
                                null
                            ) {
                                onSelectionChange(selection.toggle(ids))
                            } else {
                                onOpenGroup(entry.group.id)
                            }
                        },
                        onLongClick = { onSelectionChange((selection ?: emptySet()) + ids) },
                    )
                }
            }
        }
    }
}

private fun Set<String>.toggle(ids: Set<String>): Set<String> = if (containsAll(ids)) this - ids else this + ids

@Composable
private fun GroupsPage(state: BrowserUiState, onOpen: (String) -> Unit) {
    val groups = state.groups.map { group -> group to state.tabs.filter { it.groupId == group.id } }
    if (groups.isEmpty()) {
        SwitcherEmpty(
            icon = ThetaIcons.Groups,
            title = stringResource(R.string.groups_empty_title),
            message = stringResource(R.string.groups_empty_message),
        )
        return
    }
    SwitcherGrid {
        items(groups, key = { it.first.id }) { (group, tabs) ->
            GroupCard(group, tabs, state.previews, isChecked = null, onClick = { onOpen(group.id) }, onLongClick = {})
        }
    }
}

@Composable
private fun GroupDetail(
    group: TabGroup,
    state: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = state.tabs.filter { it.groupId == group.id }
    var isMenuOpen by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(ThetaIcons.Back, stringResource(com.seasaw.theta.core.ui.R.string.action_back))
            }
            GroupDot(group.colorIndex, size = 14.dp)
            Text(
                groupLabel(group, tabs.size),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            IconButton(onClick = onEdit) { Icon(ThetaIcons.Edit, stringResource(R.string.group_edit_title)) }
            Box {
                IconButton(onClick = {
                    isMenuOpen = true
                }) { Icon(ThetaIcons.More, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.group_ungroup)) },
                        leadingIcon = { Icon(ThetaIcons.Ungroup, null, Modifier.size(20.dp)) },
                        onClick = {
                            isMenuOpen = false
                            onEvent(BrowserUiEvent.Ungroup(group.id))
                            onBack()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.group_close)) },
                        leadingIcon = { Icon(ThetaIcons.Close, null, Modifier.size(20.dp)) },
                        onClick = {
                            isMenuOpen = false
                            onEvent(BrowserUiEvent.CloseGroup(group.id))
                            onBack()
                        },
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            SwitcherGrid {
                items(tabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        preview = state.previews[tab.id],
                        isCurrent = tab.id == state.selectedTabId,
                        isChecked = null,
                        onClick = { onEvent(BrowserUiEvent.SelectTab(tab.id)) },
                        onLongClick = {},
                        onClose = { onEvent(BrowserUiEvent.CloseTab(tab.id)) },
                    )
                }
            }
        }
        SwitcherBottomBar(
            onAdd = { onEvent(BrowserUiEvent.NewTab(group.isIncognito, group.id)) },
            addLabel = stringResource(R.string.group_new_tab),
            onDone = onBack,
        )
    }
}

@Composable
private fun SwitcherGrid(content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(
    tab: BrowserTab,
    preview: Bitmap?,
    isCurrent: Boolean,
    isChecked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val title = tab.title.ifEmpty {
        if (tab.isNewTab) stringResource(R.string.new_tab) else UrlUtils.displayUrl(tab.url)
    }
    val highlighted = isCurrent || isChecked == true
    Column(
        Modifier
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) colors.primary else colors.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(TabSwitcherTestTags.CARD_PREFIX + tab.id),
    ) {
        // The page itself comes first: it is what people recognise a tab by, more than its title.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_ASPECT)
                .padding(start = 4.dp, end = 4.dp, top = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                val image = remember(preview) { preview.asImageBitmap() }
                Image(
                    image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PreviewPlaceholder(tab)
            }
            if (isChecked != null) SelectionMark(isChecked, Modifier.align(Alignment.TopEnd))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabIcon(tab, size = 18.dp)
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            if (isChecked == null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag(TabSwitcherTestTags.CLOSE_PREFIX + tab.id),
                ) { Icon(ThetaIcons.Close, stringResource(R.string.tabs_close_tab), Modifier.size(18.dp)) }
            }
        }
    }
}

/** The tick shown on a card in selection mode, on a backing disc so it reads over any page picture. */
@Composable
private fun SelectionMark(isChecked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .padding(8.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isChecked) ThetaIcons.CheckCircle else ThetaIcons.Circle,
            null,
            tint = if (isChecked) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** What a card shows before a picture exists: the site's icon, or the new-tab mark. */
@Composable
private fun PreviewPlaceholder(tab: BrowserTab) {
    val colors = MaterialTheme.colorScheme
    when {
        tab.isNewTab -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (tab.isIncognito) ThetaIcons.Private else ThetaIcons.Search,
                null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Text(
                stringResource(R.string.new_tab),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        else -> TabIcon(tab, size = 48.dp)
    }
}

@Composable
private fun TabIcon(tab: BrowserTab, size: androidx.compose.ui.unit.Dp) {
    val label = UrlUtils.hostOf(tab.url)?.let(UrlUtils::registrableDomain) ?: tab.title.ifEmpty { "?" }
    when {
        tab.favicon != null -> {
            val icon = remember(tab.favicon) { tab.favicon.asImageBitmap() }
            SiteIcon(label, favicon = icon, size = size)
        }

        tab.isIncognito -> Icon(
            ThetaIcons.Private,
            stringResource(R.string.incognito_mode),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size),
        )

        tab.isNewTab -> Icon(
            ThetaIcons.Search,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size),
        )

        else -> SiteIcon(label, size = size)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(
    group: TabGroup,
    tabs: List<BrowserTab>,
    previews: Map<String, Bitmap>,
    isChecked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val accent = GroupPalette.color(group.colorIndex)
    Column(
        Modifier
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(if (isChecked == true) 2.dp else 1.5.dp, if (isChecked == true) colors.primary else accent, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(TabSwitcherTestTags.GROUP_PREFIX + group.id),
    ) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PREVIEW_ASPECT)
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(2) { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) { column ->
                            val index = row * 2 + column
                            MosaicCell(
                                tab = tabs.getOrNull(index),
                                preview = tabs.getOrNull(index)?.let { previews[it.id] },
                                extra = if (index == MOSAIC_LAST && tabs.size > MOSAIC_SIZE) {
                                    tabs.size - MOSAIC_LAST
                                } else {
                                    0
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (isChecked != null) SelectionMark(isChecked, Modifier.align(Alignment.TopEnd))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupDot(group.colorIndex)
            Text(
                groupLabel(group, tabs.size),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            if (group.isIncognito) {
                Icon(ThetaIcons.Private, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            } else {
                Text(
                    tabs.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MosaicCell(tab: BrowserTab?, preview: Bitmap?, extra: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        when {
            extra > 0 -> Text(
                "+$extra",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            preview != null -> {
                val image = remember(preview) { preview.asImageBitmap() }
                Image(
                    image,
                    null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            tab != null -> TabIcon(tab, size = 28.dp)
        }
    }
}

@Composable
private fun SwitcherEmpty(icon: ImageVector, title: String, message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private const val PREVIEW_ASPECT = 0.8f
private const val MOSAIC_SIZE = 4
private const val MOSAIC_LAST = 3

@ThemePreviews
@Composable
private fun TabSwitcherPreview() {
    ThetaTheme(darkTheme = true) {
        TabSwitcher(
            state = BrowserUiState(
                tabs = listOf(
                    BrowserTab("1", "https://news.example.co.in/today", "Today's headlines"),
                    BrowserTab("2", "https://weather.example.com", "Weather in Pune", groupId = "g"),
                    BrowserTab("3", "https://cricket.example.com", "Live score", groupId = "g"),
                    BrowserTab("4"),
                ),
                groups = listOf(TabGroup("g", "Cricket", 1)),
                selectedTabId = "1",
            ),
            onEvent = {},
            navigation = SwitcherNavigation(),
        )
    }
}
