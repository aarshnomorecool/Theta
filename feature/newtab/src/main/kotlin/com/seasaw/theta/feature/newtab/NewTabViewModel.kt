package com.seasaw.theta.feature.newtab

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasaw.theta.core.common.url.AddressBarParser
import com.seasaw.theta.core.common.url.AddressInput
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.data.FaviconStore
import com.seasaw.theta.core.data.HistoryRepository
import com.seasaw.theta.core.data.ImageFileStore
import com.seasaw.theta.core.data.PrivacyStats
import com.seasaw.theta.core.data.PrivacyStatsRepository
import com.seasaw.theta.core.data.SpeedDialRepository
import com.seasaw.theta.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpeedDialSite(val id: Long, val url: String, val title: String, val icon: Bitmap? = null)

data class RecentSite(val url: String, val title: String, val icon: Bitmap? = null)

data class NewTabUiState(
    /** Sites the user pinned. */
    val speedDial: List<SpeedDialSite> = emptyList(),
    /** The user's own most visited sites, filling the grid after the pinned ones. */
    val topSites: List<RecentSite> = emptyList(),
    val recentSites: List<RecentSite> = emptyList(),
    val stats: PrivacyStats? = null,
    val showStats: Boolean = true,
    val showRecent: Boolean = true,
    val wallpaper: Wallpaper? = null,
)

sealed interface NewTabUiEvent {
    data class AddSpeedDial(val address: String, val name: String) : NewTabUiEvent
    data class RemoveSpeedDial(val id: Long) : NewTabUiEvent

    /** The page came on screen: a new tab, or the app came back. Shows the next photo. */
    data object Shown : NewTabUiEvent
}

@HiltViewModel
class NewTabViewModel @Inject constructor(
    private val speedDial: SpeedDialRepository,
    history: HistoryRepository,
    stats: PrivacyStatsRepository,
    private val settings: SettingsRepository,
    private val favicons: FaviconStore,
    private val wallpapers: Wallpapers,
) : ViewModel() {
    private val wallpaper = MutableStateFlow<Wallpaper?>(null)

    private val sites = combine(
        speedDial.observeAll(),
        history.observeMostVisited(TOP_QUERY_LIMIT),
        history.observeRecent(RECENT_QUERY_LIMIT),
        // A newly saved icon redraws the tiles that use it.
        favicons.updates.onStart { emit(ImageFileStore.CLEARED) },
    ) { dial, mostVisited, recent, _ ->
        val pinned = dial.map { SpeedDialSite(it.id, it.url, it.title, iconFor(it.url)) }
        val pinnedSites = pinned.map { siteKey(it.url) }.toSet()
        val top = distinctBySite(mostVisited.map { RecentSite(it.url, it.title) }, limit = GRID_SIZE)
            .filter { siteKey(it.url) !in pinnedSites }
            .take((GRID_SIZE - pinned.size).coerceAtLeast(0))
            .map { it.copy(icon = iconFor(it.url)) }
        Triple(pinned, top, distinctBySite(recent.map { RecentSite(it.url, it.title, iconFor(it.url)) }))
    }

    val state: StateFlow<NewTabUiState> = combine(
        sites,
        stats.stats,
        settings.settings,
        wallpaper,
    ) { (pinned, top, recent), counts, prefs, photo ->
        NewTabUiState(
            speedDial = pinned,
            topSites = top,
            recentSites = recent,
            stats = counts,
            showStats = prefs.ntpShowPrivacyStats,
            showRecent = prefs.ntpShowRecentSites,
            wallpaper = photo.takeIf { prefs.ntpBackgroundPhotos },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NewTabUiState())

    init {
        stats.start()
    }

    fun onEvent(event: NewTabUiEvent) {
        when (event) {
            is NewTabUiEvent.AddSpeedDial -> add(event.address, event.name)

            is NewTabUiEvent.RemoveSpeedDial -> viewModelScope.launch { speedDial.remove(event.id) }

            NewTabUiEvent.Shown -> viewModelScope.launch {
                if (settings.settings.first().ntpBackgroundPhotos) wallpaper.value = wallpapers.next()
            }
        }
    }

    private suspend fun iconFor(url: String): Bitmap? = UrlUtils.hostOf(url)?.let { favicons.loadFor(it) }

    /** Something typed that is not an address is ignored rather than saved as a tile that would open nothing useful. */
    private fun add(address: String, name: String) {
        val url = (AddressBarParser.parse(address) as? AddressInput.Url)?.url ?: return
        val title = name.trim().ifEmpty { UrlUtils.hostOf(url)?.removePrefix("www.") ?: url }
        viewModelScope.launch { speedDial.add(url, title) }
    }

    internal companion object {
        const val MAX_RECENT_SITES = 5
        const val GRID_SIZE = 8
        private const val RECENT_QUERY_LIMIT = 40
        private const val TOP_QUERY_LIMIT = 60
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        private fun siteKey(url: String): String = UrlUtils.hostOf(url)?.let(UrlUtils::registrableDomain) ?: url

        /** Ten pages from one site should be one row, or a single visit would fill the whole list. */
        fun distinctBySite(sites: List<RecentSite>, limit: Int = MAX_RECENT_SITES): List<RecentSite> = sites
            .distinctBy { siteKey(it.url) }
            .take(limit)
    }
}
