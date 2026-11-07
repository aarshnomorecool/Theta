package com.seasaw.theta.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasaw.theta.core.analytics.Analytics
import com.seasaw.theta.core.analytics.AnalyticsEvent
import com.seasaw.theta.core.common.url.SearchEngine
import com.seasaw.theta.core.config.AppConfig
import com.seasaw.theta.core.data.AdBlockExemptionsRepository
import com.seasaw.theta.core.data.BundledPhotos
import com.seasaw.theta.core.data.EraseRequest
import com.seasaw.theta.core.data.LocalDataEraser
import com.seasaw.theta.core.data.PhotoCredit
import com.seasaw.theta.core.data.PrivacyStats
import com.seasaw.theta.core.data.PrivacyStatsRepository
import com.seasaw.theta.core.data.SitePermissionsRepository
import com.seasaw.theta.core.data.settings.AppLanguage
import com.seasaw.theta.core.data.settings.SettingFlag
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.data.settings.ThemeMode
import com.seasaw.theta.core.data.settings.UserSettings
import com.seasaw.theta.core.webview.WebDataCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a site was allowed or refused, for the site settings page. */
data class SiteDecision(val host: String, val allowed: List<String>, val blocked: List<String>)

enum class SettingsMessage { DATA_CLEARED, STATS_RESET }

/** The boxes on the clear browsing data page. */
data class ClearSelection(
    val history: Boolean = true,
    val tabs: Boolean = true,
    val siteData: Boolean = true,
    val bookmarks: Boolean = false,
    val speedDial: Boolean = false,
    val downloads: Boolean = false,
    val sitePreferences: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !(history || tabs || siteData || bookmarks || speedDial || downloads || sitePreferences)
}

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val privacyPolicyUrl: String = "",
    val message: SettingsMessage? = null,
    val stats: PrivacyStats? = null,
    val exemptSites: List<String> = emptyList(),
    val siteDecisions: List<SiteDecision> = emptyList(),
    val photoCredits: List<PhotoCredit> = emptyList(),
)

sealed interface SettingsUiEvent {
    data class SetTheme(val mode: ThemeMode) : SettingsUiEvent
    data class SetLanguage(val language: AppLanguage) : SettingsUiEvent
    data class SetSearchEngine(val engine: SearchEngine) : SettingsUiEvent
    data class SetAdBlock(val enabled: Boolean) : SettingsUiEvent
    data class SetLiteMode(val enabled: Boolean) : SettingsUiEvent
    data class SetClearOnExit(val enabled: Boolean) : SettingsUiEvent
    data class SetDiagnostics(val enabled: Boolean) : SettingsUiEvent
    data class Toggle(val setting: SettingFlag, val enabled: Boolean) : SettingsUiEvent
    data class SetTextZoom(val percent: Int) : SettingsUiEvent
    data class Clear(val selection: ClearSelection) : SettingsUiEvent
    data class RemoveAdException(val host: String) : SettingsUiEvent
    data class ForgetSite(val host: String) : SettingsUiEvent
    data object DeleteAllData : SettingsUiEvent
    data object ResetPrivacyStats : SettingsUiEvent
    data object MessageShown : SettingsUiEvent
    data object DefaultBrowserPromptShown : SettingsUiEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val eraser: LocalDataEraser,
    private val webDataCleaner: WebDataCleaner,
    private val analytics: Analytics,
    private val stats: PrivacyStatsRepository,
    private val exemptions: AdBlockExemptionsRepository,
    private val permissions: SitePermissionsRepository,
    photos: BundledPhotos,
    config: AppConfig,
) : ViewModel() {
    private val message = MutableStateFlow<SettingsMessage?>(null)
    private val credits = MutableStateFlow<List<PhotoCredit>>(emptyList())

    private val sites = combine(exemptions.observeHosts(), permissions.observeAll()) { exempt, decisions ->
        exempt.sorted() to decisions.groupBy { it.host }.map { (host, entries) ->
            SiteDecision(
                host = host,
                allowed = entries.filter { it.granted }.map { it.permission },
                blocked = entries.filterNot { it.granted }.map { it.permission },
            )
        }
    }

    val state: StateFlow<SettingsUiState> = combine(
        settings.settings,
        message,
        stats.stats,
        sites,
        credits,
    ) { current, shown, counts, (exempt, decisions), photoCredits ->
        SettingsUiState(
            settings = current,
            privacyPolicyUrl = config.privacyPolicyUrl,
            message = shown,
            stats = counts,
            exemptSites = exempt,
            siteDecisions = decisions,
            photoCredits = photoCredits,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    init {
        stats.start()
        viewModelScope.launch { credits.value = photos.credits() }
    }

    @Suppress("CyclomaticComplexMethod") // One line per event, which is the clearest form for this.
    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.SetTheme -> launchAndTrack(AnalyticsEvent.ThemeChanged(event.mode.id)) {
                settings.setThemeMode(event.mode)
            }

            is SettingsUiEvent.SetLanguage -> launchAndTrack(AnalyticsEvent.LanguageChanged(event.language.id)) {
                settings.setLanguage(event.language)
            }

            is SettingsUiEvent.SetSearchEngine -> launchAndTrack(AnalyticsEvent.SearchEngineChanged(event.engine.id)) {
                settings.setSearchEngine(event.engine)
            }

            is SettingsUiEvent.SetAdBlock -> launchAndTrack(AnalyticsEvent.AdBlockToggled(event.enabled)) {
                settings.setAdBlockEnabled(event.enabled)
            }

            is SettingsUiEvent.SetLiteMode -> launchAndTrack(AnalyticsEvent.LiteModeToggled(event.enabled)) {
                settings.setLiteMode(event.enabled)
            }

            is SettingsUiEvent.SetClearOnExit -> viewModelScope.launch { settings.setClearOnExit(event.enabled) }

            is SettingsUiEvent.SetDiagnostics -> viewModelScope.launch { settings.setDiagnosticsConsent(event.enabled) }

            is SettingsUiEvent.Toggle -> viewModelScope.launch { settings.setFlag(event.setting, event.enabled) }

            is SettingsUiEvent.SetTextZoom -> viewModelScope.launch { settings.setTextZoomPercent(event.percent) }

            is SettingsUiEvent.Clear -> clear(event.selection)

            is SettingsUiEvent.RemoveAdException -> viewModelScope.launch { exemptions.removeExemption(event.host) }

            is SettingsUiEvent.ForgetSite -> viewModelScope.launch { permissions.forgetHost(event.host) }

            SettingsUiEvent.DeleteAllData -> erase(EraseRequest.Everything, clearWebData = true)

            SettingsUiEvent.ResetPrivacyStats -> viewModelScope.launch {
                stats.reset()
                message.value = SettingsMessage.STATS_RESET
            }

            SettingsUiEvent.MessageShown -> message.value = null

            SettingsUiEvent.DefaultBrowserPromptShown -> analytics.track(AnalyticsEvent.DefaultBrowserPromptShown)
        }
    }

    private fun clear(selection: ClearSelection) {
        if (selection.isEmpty) return
        val request = EraseRequest(
            history = selection.history,
            tabs = selection.tabs,
            bookmarks = selection.bookmarks,
            speedDial = selection.speedDial,
            downloads = selection.downloads,
            sitePreferences = selection.sitePreferences,
        )
        erase(request, clearWebData = selection.siteData)
    }

    /** Privacy stats are left alone on purpose, even by "delete everything": they have their own reset. */
    private fun erase(request: EraseRequest, clearWebData: Boolean) {
        viewModelScope.launch {
            eraser.erase(request)
            if (clearWebData) webDataCleaner.clearWebData()
            analytics.track(AnalyticsEvent.LocalDataCleared)
            message.value = SettingsMessage.DATA_CLEARED
        }
    }

    private fun launchAndTrack(event: AnalyticsEvent, action: suspend () -> Unit) {
        analytics.track(event)
        viewModelScope.launch { action() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
