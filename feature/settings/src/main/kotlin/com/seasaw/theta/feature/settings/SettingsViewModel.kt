package com.seasaw.theta.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasaw.theta.core.analytics.Analytics
import com.seasaw.theta.core.analytics.AnalyticsEvent
import com.seasaw.theta.core.common.url.SearchEngine
import com.seasaw.theta.core.config.AppConfig
import com.seasaw.theta.core.data.EraseRequest
import com.seasaw.theta.core.data.LocalDataEraser
import com.seasaw.theta.core.data.settings.AppLanguage
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

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val privacyPolicyUrl: String = "",
    val justCleared: Boolean = false,
)

sealed interface SettingsUiEvent {
    data class SetTheme(val mode: ThemeMode) : SettingsUiEvent
    data class SetLanguage(val language: AppLanguage) : SettingsUiEvent
    data class SetSearchEngine(val engine: SearchEngine) : SettingsUiEvent
    data class SetAdBlock(val enabled: Boolean) : SettingsUiEvent
    data class SetLiteMode(val enabled: Boolean) : SettingsUiEvent
    data class SetClearOnExit(val enabled: Boolean) : SettingsUiEvent
    data class SetDiagnostics(val enabled: Boolean) : SettingsUiEvent
    data object ClearBrowsingData : SettingsUiEvent
    data object DeleteAllData : SettingsUiEvent
    data object ClearedMessageShown : SettingsUiEvent
    data object DefaultBrowserPromptShown : SettingsUiEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val eraser: LocalDataEraser,
    private val webDataCleaner: WebDataCleaner,
    private val analytics: Analytics,
    config: AppConfig,
) : ViewModel() {
    private val justCleared = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, justCleared) { current, cleared ->
        SettingsUiState(current, config.privacyPolicyUrl, cleared)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

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

            SettingsUiEvent.ClearBrowsingData -> erase(EraseRequest.BrowsingTraces)

            SettingsUiEvent.DeleteAllData -> erase(EraseRequest.Everything)

            SettingsUiEvent.ClearedMessageShown -> justCleared.value = false

            SettingsUiEvent.DefaultBrowserPromptShown -> analytics.track(AnalyticsEvent.DefaultBrowserPromptShown)
        }
    }

    private fun launchAndTrack(event: AnalyticsEvent, action: suspend () -> Unit) {
        analytics.track(event)
        viewModelScope.launch { action() }
    }

    /** Both halves are needed: the app's own tables, and what the WebView stores for sites. */
    private fun erase(request: EraseRequest) {
        viewModelScope.launch {
            eraser.erase(request)
            webDataCleaner.clearWebData()
            analytics.track(AnalyticsEvent.LocalDataCleared)
            justCleared.value = true
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
