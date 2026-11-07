package com.seasaw.theta.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasaw.theta.core.ads.AdPlacement
import com.seasaw.theta.core.ads.AdSlot
import com.seasaw.theta.core.ui.ConfirmDialog
import com.seasaw.theta.core.ui.EmptyState
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme
import com.seasaw.theta.core.ui.ThetaTopBar

@Composable
fun HistoryRoute(onOpenUrl: (String) -> Unit, onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(state, onOpenUrl, onBack, viewModel::onEvent) { AdSlot(AdPlacement.HISTORY) }
}

@Composable
internal fun HistoryScreen(
    state: HistoryUiState,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onEvent: (HistoryUiEvent) -> Unit,
    adSlot: @Composable () -> Unit,
) {
    var isConfirmingClear by remember { mutableStateOf(false) }
    var siteToDelete by remember { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding()) {
            ThetaTopBar(stringResource(R.string.history_title), onBack) {
                if (!state.isEmpty) {
                    TextButton(onClick = { isConfirmingClear = true }) {
                        Text(stringResource(CoreUiR.string.action_clear_all))
                    }
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(HistoryUiEvent.QueryChanged(it)) },
                placeholder = { Text(stringResource(R.string.history_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(
                    selected = state.isGroupedBySite,
                    onClick = { onEvent(HistoryUiEvent.SetGroupedBySite(!state.isGroupedBySite)) },
                    label = { Text(stringResource(R.string.history_group_by_site)) },
                    leadingIcon = if (state.isGroupedBySite) {
                        { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag(HistoryTestTags.GROUP_TOGGLE),
                )
            }
            if (state.isEmpty) {
                EmptyState(
                    ThetaIcons.History,
                    stringResource(
                        if (state.query.isBlank()) R.string.history_empty_title else R.string.history_no_matches,
                    ),
                    stringResource(R.string.history_empty_message),
                    Modifier.weight(1f),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    if (state.isGroupedBySite) {
                        groupedVisits(state, onOpenUrl, onEvent) { siteToDelete = it }
                    } else {
                        items(state.rows, key = { it.id }) { row ->
                            VisitRow(row, { onOpenUrl(row.url) }, { onEvent(HistoryUiEvent.Delete(row.id)) })
                        }
                    }
                }
            }
            adSlot()
        }
    }
    if (isConfirmingClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            message = stringResource(R.string.history_clear_message),
            confirmLabel = stringResource(CoreUiR.string.action_clear_all),
            onConfirm = {
                onEvent(HistoryUiEvent.ClearAll)
                isConfirmingClear = false
            },
            onDismiss = { isConfirmingClear = false },
        )
    }
    siteToDelete?.let { site ->
        ConfirmDialog(
            title = stringResource(R.string.history_delete_site_title, site),
            message = stringResource(R.string.history_delete_site_message),
            confirmLabel = stringResource(CoreUiR.string.action_delete),
            onConfirm = {
                onEvent(HistoryUiEvent.DeleteSite(site))
                siteToDelete = null
            },
            onDismiss = { siteToDelete = null },
        )
    }
}

/** A site visited once shows as a plain row. Grouping only appears where it saves space. */
private fun LazyListScope.groupedVisits(
    state: HistoryUiState,
    onOpenUrl: (String) -> Unit,
    onEvent: (HistoryUiEvent) -> Unit,
    onDeleteSite: (String) -> Unit,
) {
    state.groups.forEach { group ->
        if (group.rows.size == 1) {
            val row = group.latest
            item(key = "visit-${row.id}") {
                VisitRow(row, { onOpenUrl(row.url) }, { onEvent(HistoryUiEvent.Delete(row.id)) })
            }
        } else {
            val isExpanded = group.site in state.expandedSites
            item(key = "site-${group.site}") {
                SiteRow(group, isExpanded, {
                    onEvent(HistoryUiEvent.ToggleSite(group.site))
                }, { onDeleteSite(group.site) })
            }
            if (isExpanded) {
                items(group.rows, key = { "visit-${it.id}" }) { row ->
                    VisitRow(row, { onOpenUrl(row.url) }, { onEvent(HistoryUiEvent.Delete(row.id)) }, indent = 24.dp)
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun HistoryPreview() {
    val rows = listOf(
        HistoryRow(1, "https://m.youtube.com/shorts/a1", "Cricket highlights"),
        HistoryRow(2, "https://m.youtube.com/shorts/b2", "Street food in Pune"),
        HistoryRow(3, "https://news.example.in/today", "Today's headlines"),
        HistoryRow(4, "https://m.youtube.com/shorts/c3", "Monsoon timelapse"),
    )
    ThetaTheme {
        HistoryScreen(
            HistoryUiState(
                rows = rows,
                groups = HistoryViewModel.groupBySite(rows),
                expandedSites = setOf("youtube.com"),
            ),
            {},
            {},
            {},
        ) { AdSlot(AdPlacement.HISTORY) }
    }
}
