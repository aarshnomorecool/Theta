package com.seasaw.theta.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.seasaw.theta.core.ads.AdPlacement
import com.seasaw.theta.core.ads.AdSlot
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.data.HistoryRepository
import com.seasaw.theta.core.ui.ConfirmDialog
import com.seasaw.theta.core.ui.EmptyState
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.SiteAvatar
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme
import com.seasaw.theta.core.ui.ThetaTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryRow(val id: Long, val url: String, val title: String)

data class HistoryUiState(val query: String = "", val rows: List<HistoryRow> = emptyList())

sealed interface HistoryUiEvent {
    data class QueryChanged(val query: String) : HistoryUiEvent
    data class Delete(val id: Long) : HistoryUiEvent
    data object ClearAll : HistoryUiEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(private val history: HistoryRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HistoryUiState> = combine(
        query,
        query.flatMapLatest { if (it.isBlank()) history.observeRecent() else history.observeMatching(it.trim()) },
    ) { currentQuery, rows ->
        HistoryUiState(currentQuery, rows.map { HistoryRow(it.id, it.url, it.title) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState())

    fun onEvent(event: HistoryUiEvent) {
        when (event) {
            is HistoryUiEvent.QueryChanged -> query.value = event.query
            is HistoryUiEvent.Delete -> viewModelScope.launch { history.delete(event.id) }
            HistoryUiEvent.ClearAll -> viewModelScope.launch { history.clear() }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

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
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding()) {
            ThetaTopBar(stringResource(R.string.history_title), onBack) {
                if (state.rows.isNotEmpty()) {
                    TextButton(onClick = {
                        isConfirmingClear = true
                    }) { Text(stringResource(CoreUiR.string.action_clear_all)) }
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
            if (state.rows.isEmpty()) {
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
                    items(state.rows, key = { it.id }) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(row.url) }
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SiteAvatar(UrlUtils.hostOf(row.url)?.removePrefix("www.") ?: row.title)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(
                                    row.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    UrlUtils.displayUrl(row.url),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onEvent(HistoryUiEvent.Delete(row.id)) }) {
                                Icon(Icons.Filled.Close, stringResource(CoreUiR.string.action_delete))
                            }
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
}

@ThemePreviews
@Composable
private fun HistoryPreview() {
    ThetaTheme {
        HistoryScreen(
            HistoryUiState(rows = listOf(HistoryRow(1, "https://news.example.in/today", "Today's headlines"))),
            {},
            {},
            {},
        ) { AdSlot(AdPlacement.HISTORY) }
    }
}
