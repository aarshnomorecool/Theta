package com.seasaw.theta.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.data.HistoryRepository
import com.seasaw.theta.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryRow(val id: Long, val url: String, val title: String)

/** All visits to one site, newest first. [site] is a registrable domain, such as youtube.com. */
data class HistoryGroup(val site: String, val rows: List<HistoryRow>) {
    val latest: HistoryRow get() = rows.first()
}

data class HistoryUiState(
    val query: String = "",
    val rows: List<HistoryRow> = emptyList(),
    val isGroupedBySite: Boolean = true,
    val groups: List<HistoryGroup> = emptyList(),
    val expandedSites: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

sealed interface HistoryUiEvent {
    data class QueryChanged(val query: String) : HistoryUiEvent
    data class Delete(val id: Long) : HistoryUiEvent
    data class DeleteSite(val site: String) : HistoryUiEvent
    data class ToggleSite(val site: String) : HistoryUiEvent
    data class SetGroupedBySite(val grouped: Boolean) : HistoryUiEvent
    data object ClearAll : HistoryUiEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val expandedSites = MutableStateFlow(emptySet<String>())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows = query.flatMapLatest { text ->
        if (text.isBlank()) history.observeRecent() else history.observeMatching(text.trim())
    }.map { entities -> entities.map { HistoryRow(it.id, it.url, it.title) } }

    private val isGrouped = settings.settings.map { it.historyGroupedBySite }.distinctUntilChanged()

    val state: StateFlow<HistoryUiState> = combine(query, rows, isGrouped, expandedSites) { text, list, grouped, open ->
        HistoryUiState(
            query = text,
            rows = list,
            isGroupedBySite = grouped,
            groups = if (grouped) groupBySite(list) else emptyList(),
            expandedSites = open,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState())

    fun onEvent(event: HistoryUiEvent) {
        when (event) {
            is HistoryUiEvent.QueryChanged -> query.value = event.query

            is HistoryUiEvent.Delete -> viewModelScope.launch { history.delete(event.id) }

            is HistoryUiEvent.DeleteSite -> viewModelScope.launch { history.deleteSite(event.site) }

            is HistoryUiEvent.ToggleSite -> expandedSites.value = expandedSites.value.toggle(event.site)

            is HistoryUiEvent.SetGroupedBySite -> viewModelScope.launch {
                settings.setHistoryGroupedBySite(event.grouped)
            }

            HistoryUiEvent.ClearAll -> viewModelScope.launch { history.clear() }
        }
    }

    private fun Set<String>.toggle(site: String): Set<String> = if (site in this) this - site else this + site

    internal companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * Groups visits by site, keeping the order of each site's most recent visit. A page with no site, such as
         * about:blank, gets a group of its own rather than being merged with unrelated pages.
         */
        fun groupBySite(rows: List<HistoryRow>): List<HistoryGroup> = rows
            .groupBy { row -> UrlUtils.siteOf(row.url) ?: row.url }
            .map { (site, visits) -> HistoryGroup(site, visits) }
    }
}
