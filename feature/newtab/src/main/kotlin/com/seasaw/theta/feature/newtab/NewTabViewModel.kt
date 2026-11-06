package com.seasaw.theta.feature.newtab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasaw.theta.core.common.url.AddressBarParser
import com.seasaw.theta.core.common.url.AddressInput
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.data.HistoryRepository
import com.seasaw.theta.core.data.SpeedDialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpeedDialSite(val id: Long, val url: String, val title: String)

data class RecentSite(val url: String, val title: String)

data class NewTabUiState(
    val speedDial: List<SpeedDialSite> = emptyList(),
    val recentSites: List<RecentSite> = emptyList(),
)

sealed interface NewTabUiEvent {
    data class AddSpeedDial(val address: String, val name: String) : NewTabUiEvent
    data class RemoveSpeedDial(val id: Long) : NewTabUiEvent
}

@HiltViewModel
class NewTabViewModel @Inject constructor(private val speedDial: SpeedDialRepository, history: HistoryRepository) :
    ViewModel() {
    val state: StateFlow<NewTabUiState> = combine(
        speedDial.observeAll(),
        history.observeRecent(RECENT_QUERY_LIMIT),
    ) { dial, visits ->
        NewTabUiState(
            speedDial = dial.map { SpeedDialSite(it.id, it.url, it.title) },
            recentSites = distinctBySite(visits.map { RecentSite(it.url, it.title) }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NewTabUiState())

    fun onEvent(event: NewTabUiEvent) {
        when (event) {
            is NewTabUiEvent.AddSpeedDial -> add(event.address, event.name)
            is NewTabUiEvent.RemoveSpeedDial -> viewModelScope.launch { speedDial.remove(event.id) }
        }
    }

    /** Something typed that is not an address is ignored rather than saved as a tile that would open nothing useful. */
    private fun add(address: String, name: String) {
        val url = (AddressBarParser.parse(address) as? AddressInput.Url)?.url ?: return
        val title = name.trim().ifEmpty { UrlUtils.hostOf(url)?.removePrefix("www.") ?: url }
        viewModelScope.launch { speedDial.add(url, title) }
    }

    internal companion object {
        const val MAX_RECENT_SITES = 5
        private const val RECENT_QUERY_LIMIT = 40
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Ten pages from one site should be one row, or a single visit would fill the whole list. */
        fun distinctBySite(sites: List<RecentSite>): List<RecentSite> = sites
            .distinctBy { UrlUtils.hostOf(it.url)?.let(UrlUtils::registrableDomain) ?: it.url }
            .take(MAX_RECENT_SITES)
    }
}
