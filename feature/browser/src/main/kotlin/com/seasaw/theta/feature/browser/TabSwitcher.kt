package com.seasaw.theta.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seasaw.theta.core.ads.AdPlacement
import com.seasaw.theta.core.ads.AdSlot
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.ui.SiteAvatar
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme

internal object TabSwitcherTestTags {
    const val CARD_PREFIX = "tab_card_"
    const val CLOSE_PREFIX = "tab_close_"
    const val NEW_TAB = "switcher_new_tab"
    const val INCOGNITO_SECTION = "switcher_incognito"
    const val REGULAR_SECTION = "switcher_regular"
}

/**
 * Cards show the title and address, not a screenshot of the page. A screenshot is web content, and this screen carries
 * an ad, so screenshots would break ads rule 1. Skipping them also saves memory on the phones this app targets.
 *
 * The ad sits at the end of the list and never next to a tab's close button or the new-tab button (ads rule 5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabSwitcher(
    tabs: List<BrowserTab>,
    selectedTabId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseIncognito: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    adSlot: @Composable (visibleHosts: List<String?>) -> Unit = { hosts ->
        AdSlot(AdPlacement.TAB_SWITCHER, Modifier.padding(top = 24.dp), hosts)
    },
) {
    val selectedIsIncognito = tabs.firstOrNull { it.id == selectedTabId }?.isIncognito == true
    var showIncognito by remember { mutableStateOf(selectedIsIncognito) }
    val shown = tabs.filter { it.isIncognito == showIncognito }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(stringResource(R.string.tabs_title)) },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, stringResource(R.string.tabs_close_switcher))
                }
            },
            actions = {
                IconButton(onClick = { onNewTab(showIncognito) }, Modifier.testTag(TabSwitcherTestTags.NEW_TAB)) {
                    Icon(Icons.Filled.Add, stringResource(R.string.new_tab))
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = if (showIncognito) 1 else 0) {
            Tab(
                selected = !showIncognito,
                onClick = { showIncognito = false },
                text = { Text(stringResource(R.string.tabs_regular, tabs.count { !it.isIncognito })) },
                modifier = Modifier.testTag(TabSwitcherTestTags.REGULAR_SECTION),
            )
            Tab(
                selected = showIncognito,
                onClick = { showIncognito = true },
                text = { Text(stringResource(R.string.tabs_incognito, tabs.count { it.isIncognito })) },
                modifier = Modifier.testTag(TabSwitcherTestTags.INCOGNITO_SECTION),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(if (showIncognito) R.string.tabs_no_incognito else R.string.tabs_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { tab ->
                TabCard(tab, tab.id == selectedTabId, { onSelect(tab.id) }, { onClose(tab.id) })
            }
            if (showIncognito && shown.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = {
                            onCloseIncognito()
                            showIncognito = false
                        },
                    ) { Text(stringResource(R.string.tabs_close_incognito)) }
                }
            }
            if (!showIncognito) {
                item(key = "ad") { adSlot(shown.map { UrlUtils.hostOf(it.url) }) }
            }
        }
    }
}

@Composable
private fun TabCard(tab: BrowserTab, isSelected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val title = tab.title.ifEmpty {
        if (tab.isNewTab) stringResource(R.string.new_tab) else UrlUtils.displayUrl(tab.url)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.primary else colors.outlineVariant, shape)
            .clickable(onClick = onSelect)
            .testTag(TabSwitcherTestTags.CARD_PREFIX + tab.id)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab.isIncognito) {
            Icon(ThetaIcons.Incognito, stringResource(R.string.incognito_mode), tint = colors.onSurfaceVariant)
        } else {
            SiteAvatar(UrlUtils.hostOf(tab.url)?.removePrefix("www.") ?: title)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!tab.isNewTab) {
                Text(
                    UrlUtils.displayUrl(tab.url),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onClose, Modifier.testTag(TabSwitcherTestTags.CLOSE_PREFIX + tab.id)) {
            Icon(Icons.Filled.Close, stringResource(R.string.tabs_close_tab))
        }
    }
}

@ThemePreviews
@Composable
private fun TabSwitcherPreview() {
    ThetaTheme {
        TabSwitcher(
            tabs = listOf(
                BrowserTab("1", "https://news.example.co.in/today", "Today's headlines"),
                BrowserTab("2", "https://weather.example.com", "Weather in Pune"),
                BrowserTab("3"),
            ),
            selectedTabId = "2",
            onSelect = {},
            onClose = {},
            onNewTab = {},
            onCloseIncognito = {},
            onDismiss = {},
            adSlot = {},
        )
    }
}
