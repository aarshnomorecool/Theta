package com.seasaw.theta.feature.browser

import android.webkit.URLUtil
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasaw.theta.core.analytics.Analytics
import com.seasaw.theta.core.analytics.AnalyticsEvent
import com.seasaw.theta.core.common.AppError
import com.seasaw.theta.core.common.AppResult
import com.seasaw.theta.core.common.ApplicationScope
import com.seasaw.theta.core.common.MainDispatcher
import com.seasaw.theta.core.common.url.AddressBarParser
import com.seasaw.theta.core.common.url.SearchEngine
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.data.AdBlockExemptionsRepository
import com.seasaw.theta.core.data.BookmarksRepository
import com.seasaw.theta.core.data.DownloadsRepository
import com.seasaw.theta.core.data.EraseRequest
import com.seasaw.theta.core.data.HistoryRepository
import com.seasaw.theta.core.data.LocalDataEraser
import com.seasaw.theta.core.data.SpeedDialRepository
import com.seasaw.theta.core.data.TabsRepository
import com.seasaw.theta.core.data.db.TabEntity
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.webview.DownloadEnqueuer
import com.seasaw.theta.core.webview.ExternalLaunchResult
import com.seasaw.theta.core.webview.ExternalLink
import com.seasaw.theta.core.webview.ExternalLinks
import com.seasaw.theta.core.webview.IncognitoProfiles
import com.seasaw.theta.core.webview.TabPreferences
import com.seasaw.theta.core.webview.TabSpec
import com.seasaw.theta.core.webview.WebDataCleaner
import com.seasaw.theta.core.webview.WebViewFactory
import com.seasaw.theta.core.webview.WebViewPool
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns the open tabs. All of this runs on the main thread, because WebViews require it. */
@Suppress("TooManyFunctions") // One small handler per user action, which is this screen's whole job.
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val pool: WebViewPool,
    private val webViewFactory: WebViewFactory,
    private val tabsRepository: TabsRepository,
    private val history: HistoryRepository,
    private val bookmarks: BookmarksRepository,
    private val speedDial: SpeedDialRepository,
    private val downloads: DownloadsRepository,
    private val exemptions: AdBlockExemptionsRepository,
    private val settings: SettingsRepository,
    private val eraser: LocalDataEraser,
    private val webDataCleaner: WebDataCleaner,
    private val incognito: IncognitoProfiles,
    private val downloadEnqueuer: DownloadEnqueuer,
    private val externalLinks: ExternalLinks,
    private val analytics: Analytics,
    @ApplicationScope private val appScope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private var searchEngine = SearchEngine.Default
    private var isDarkTheme = false

    init {
        observeSettings()
        observeSelectedTabFacts()
        eraser.erasures.onEach(::onDataErased).launchIn(viewModelScope)
        viewModelScope.launch { restoreTabs() }
    }

    /** The UI asks for the view each time [BrowserUiState.webViewGeneration] changes. */
    fun webView(tabId: String): WebView? = pool.view(tabId)

    /** The Activity provides the context for page popups. Pass null when it goes away, to avoid leaking it. */
    fun attachHost(activity: android.content.Context?) = pool.attachHost(activity)

    /** Opens a link handed to us by another app. */
    fun openFromIntent(url: String) = newTab(isIncognito = false, initialUrl = url)

    fun launchExternal(link: ExternalLink, activity: android.content.Context) {
        val result = externalLinks.launch(link, activity)
        if (result is ExternalLaunchResult.NoAppInstalled) {
            val fallback = result.fallbackUrl
            if (fallback != null) navigateSelected(fallback) else showMessage(BrowserMessage.NO_APP_FOUND)
        }
    }

    fun onEvent(event: BrowserUiEvent) {
        when (event) {
            is BrowserUiEvent.Submit -> submit(event.text)

            is BrowserUiEvent.OpenUrl -> navigateSelected(event.url)

            is BrowserUiEvent.NewTab -> newTab(event.isIncognito)

            is BrowserUiEvent.SelectTab -> select(event.id)

            is BrowserUiEvent.CloseTab -> closeTab(event.id)

            BrowserUiEvent.CloseIncognitoTabs -> closeIncognitoTabs()

            BrowserUiEvent.GoBack -> goBack()

            BrowserUiEvent.GoForward -> selectedWebView()?.goForward()

            BrowserUiEvent.GoHome -> goHome()

            BrowserUiEvent.Reload -> reload()

            BrowserUiEvent.StopLoading -> selectedWebView()?.stopLoading()

            is BrowserUiEvent.ShowTabSwitcher -> _state.update { it.copy(isTabSwitcherOpen = event.visible) }

            BrowserUiEvent.ToggleBookmark -> toggleBookmark()

            BrowserUiEvent.AddToSpeedDial -> addToSpeedDial()

            BrowserUiEvent.ToggleSiteAdBlock -> toggleSiteAdBlock()

            BrowserUiEvent.ToggleDesktopMode -> toggleDesktopMode()

            BrowserUiEvent.ToggleLiteMode -> toggleLiteMode()

            is BrowserUiEvent.SetTextZoom -> viewModelScope.launch { settings.setTextZoomPercent(event.percent) }

            is BrowserUiEvent.ThemeChanged -> themeChanged(event.isDark)

            BrowserUiEvent.StartFind -> startFind()

            is BrowserUiEvent.FindQuery -> findQuery(event.query)

            is BrowserUiEvent.FindStep -> selectedWebView()?.findNext(event.forward)

            BrowserUiEvent.CloseFind -> closeFind()

            is BrowserUiEvent.RespondPermission -> respondPermission(event.granted, event.remember)

            is BrowserUiEvent.RespondOpenExternally -> clearPrompt()

            is BrowserUiEvent.RespondDownload -> respondDownload(event.accept)

            BrowserUiEvent.ExitFullscreen -> exitFullscreen()

            BrowserUiEvent.FileChooserHandled -> _state.update { it.copy(fileChooser = null) }

            BrowserUiEvent.MessageShown -> _state.update { it.copy(message = null) }

            BrowserUiEvent.TrimMemory -> viewModelScope.launch { pool.trimToVisible(state.value.selectedTabId) }

            BrowserUiEvent.AppBackgrounded -> viewModelScope.launch {
                pool.persistAll()
                persistTabs()
            }
        }
    }

    override fun onCleared() {
        // The pool outlives this ViewModel, and its WebViews call back into it, so they must not stay alive after it.
        // viewModelScope is already cancelled by the time this runs, so the work needs a scope that is still alive.
        appScope.launch(mainDispatcher) { pool.releaseAll() }
    }

    private fun observeSettings() {
        settings.settings.onEach { current ->
            searchEngine = current.searchEngine
            webViewFactory.setLiteMode(current.liteMode)
            _state.update {
                it.copy(
                    isLiteMode = current.liteMode,
                    isAdBlockEnabled = current.adBlockEnabled,
                    textZoomPercent = current.textZoomPercent,
                )
            }
            refreshLivePreferences()
        }.launchIn(viewModelScope)
    }

    /** Keeps the menu's bookmark and "allow ads" state in step with whichever site is showing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedTabFacts() {
        val url = state.map { it.selectedTab?.url.orEmpty() }.distinctUntilChanged()
        url.flatMapLatest { if (it.isEmpty()) flowOf(false) else bookmarks.observeIsBookmarked(it) }
            .onEach { saved -> _state.update { it.copy(isBookmarked = saved) } }
            .launchIn(viewModelScope)
        combine(url, exemptions.observeHosts()) { current, hosts ->
            UrlUtils.hostOf(current)?.let(UrlUtils::registrableDomain) in hosts
        }.onEach { exempt -> _state.update { it.copy(isSiteExemptFromAdBlock = exempt) } }
            .launchIn(viewModelScope)
    }

    /** With clear-on-exit, the previous session is wiped here too: swiping the app away runs no exit code. */
    private suspend fun restoreTabs() {
        if (settings.settings.first().clearOnExit) {
            eraser.erase(EraseRequest.BrowsingTraces)
            webDataCleaner.clearWebData()
        }
        val saved = tabsRepository.restore()
        val restored = saved.map { BrowserTab(id = it.id, url = it.url, title = it.title) }
        val savedSelection = saved.firstOrNull { it.isSelected }?.id ?: restored.lastOrNull()?.id
        // A link from another app may already have opened a tab while this was reading the database. It must survive.
        _state.update { it.copy(tabs = restored + it.tabs, selectedTabId = it.selectedTabId ?: savedSelection) }
        val selected = state.value.selectedTabId
        if (selected == null) newTab(isIncognito = false) else showTab(selected)
    }

    private fun submit(text: String) {
        val url = AddressBarParser.resolve(text, searchEngine) ?: return
        navigateSelected(url)
    }

    private fun navigateSelected(url: String) {
        val tab = state.value.selectedTab ?: return
        updateTab(tab.id) { it.copy(url = url, error = null) }
        viewModelScope.launch {
            val alreadyLive = pool.view(tab.id)
            if (alreadyLive != null) {
                alreadyLive.loadUrl(url)
            } else {
                showTab(tab.id)
            }
            persistTabs()
        }
    }

    private fun newTab(isIncognito: Boolean, initialUrl: String? = null) {
        val current = state.value
        val reusable = TabListRules.reusableNewTab(current.tabs, isIncognito).takeIf { initialUrl == null }
        if (reusable != null) {
            select(reusable.id)
            return
        }
        val tab = BrowserTab(id = UUID.randomUUID().toString(), url = initialUrl.orEmpty(), isIncognito = isIncognito)
        _state.update { it.copy(tabs = it.tabs + tab, selectedTabId = tab.id, isTabSwitcherOpen = false, find = null) }
        // Asked only here because the check loads the WebView engine, which must not happen during startup.
        if (isIncognito) _state.update { it.copy(isIncognitoIsolationSupported = incognito.isIsolationSupported) }
        analytics.track(AnalyticsEvent.TabOpened(isIncognito))
        viewModelScope.launch {
            showTab(tab.id)
            persistTabs()
        }
    }

    private fun select(id: String) {
        _state.update { it.copy(selectedTabId = id, isTabSwitcherOpen = false, find = null) }
        viewModelScope.launch {
            showTab(id)
            persistTabs()
        }
    }

    /** New-tab pages stay native. Creating a WebView only on first navigation is a large part of the startup budget. */
    private suspend fun showTab(id: String) {
        val tab = state.value.tabs.firstOrNull { it.id == id } ?: return
        if (tab.isNewTab) {
            bumpGeneration()
            return
        }
        pool.acquire(specFor(tab), TabEventsBridge(this, id)) { webView -> webView.loadUrl(tab.url) }
        bumpGeneration()
    }

    private fun specFor(tab: BrowserTab) = TabSpec(tab.id, tab.url, tab.isIncognito, preferencesFor(tab))

    private fun preferencesFor(tab: BrowserTab) = TabPreferences(
        darkMode = isDarkTheme,
        textZoomPercent = state.value.textZoomPercent,
        desktopMode = tab.isDesktopMode,
        liteMode = state.value.isLiteMode,
    )

    private fun closeTab(id: String) {
        val current = state.value
        val nextSelected = if (current.selectedTabId ==
            id
        ) {
            TabListRules.nextSelection(current.tabs, id)
        } else {
            current.selectedTabId
        }
        val remaining = current.tabs.filter { it.id != id }
        _state.update { it.copy(tabs = remaining, selectedTabId = nextSelected) }
        analytics.track(AnalyticsEvent.TabClosed)
        viewModelScope.launch {
            pool.close(id)
            if (remaining.isEmpty()) {
                newTab(isIncognito = false)
            } else if (nextSelected != null) {
                showTab(nextSelected)
            }
            persistTabs()
        }
    }

    private fun closeIncognitoTabs() {
        state.value.tabs.filter { it.isIncognito }.forEach { closeTab(it.id) }
    }

    /** Called from a system back press. Returns whether there was somewhere to go inside the app. */
    private fun goBack() {
        val tab = state.value.selectedTab ?: return
        val webView = pool.view(tab.id)
        if (webView != null && webView.canGoBack()) webView.goBack() else goHome()
    }

    private fun goHome() {
        val tab = state.value.selectedTab ?: return
        viewModelScope.launch {
            pool.close(tab.id)
            updateTab(tab.id) { it.copy(url = "", title = "", canGoBack = false, canGoForward = false, error = null) }
            bumpGeneration()
            persistTabs()
        }
    }

    private fun reload() {
        val tab = state.value.selectedTab ?: return
        updateTab(tab.id) { it.copy(error = null) }
        val webView = selectedWebView()
        if (webView != null) webView.reload() else viewModelScope.launch { showTab(tab.id) }
    }

    private fun toggleBookmark() {
        val tab = state.value.selectedTab?.takeIf { !it.isNewTab } ?: return
        viewModelScope.launch {
            if (state.value.isBookmarked) {
                bookmarks.remove(tab.url)
            } else {
                bookmarks.add(tab.url, tab.title.ifEmpty { tab.url })
                analytics.track(AnalyticsEvent.BookmarkAdded)
            }
        }
    }

    private fun addToSpeedDial() {
        val tab = state.value.selectedTab?.takeIf { !it.isNewTab } ?: return
        viewModelScope.launch { speedDial.add(tab.url, tab.title.ifEmpty { UrlUtils.displayUrl(tab.url) }) }
    }

    private fun toggleSiteAdBlock() {
        val site = state.value.selectedTab?.url?.let(UrlUtils::hostOf)?.let(UrlUtils::registrableDomain) ?: return
        val exempt = !state.value.isSiteExemptFromAdBlock
        viewModelScope.launch {
            if (exempt) exemptions.exempt(site) else exemptions.removeExemption(site)
            selectedWebView()?.reload()
        }
    }

    private fun toggleDesktopMode() {
        val tab = state.value.selectedTab ?: return
        val updated = tab.copy(isDesktopMode = !tab.isDesktopMode)
        updateTab(tab.id) { updated }
        analytics.track(AnalyticsEvent.DesktopModeToggled(updated.isDesktopMode))
        pool.updatePreferences(tab.id, preferencesFor(updated))
        selectedWebView()?.reload()
    }

    private fun toggleLiteMode() {
        val enabled = !state.value.isLiteMode
        analytics.track(AnalyticsEvent.LiteModeToggled(enabled))
        viewModelScope.launch { settings.setLiteMode(enabled) }
    }

    private fun themeChanged(isDark: Boolean) {
        if (isDark == isDarkTheme) return
        isDarkTheme = isDark
        viewModelScope.launch {
            pool.recreateForTheme(state.value.selectedTabId, isDark)
            bumpGeneration()
        }
    }

    private fun refreshLivePreferences() {
        state.value.tabs.forEach { pool.updatePreferences(it.id, preferencesFor(it)) }
    }

    private fun startFind() {
        analytics.track(AnalyticsEvent.FindInPageUsed)
        _state.update { it.copy(find = FindState()) }
    }

    private fun findQuery(query: String) {
        _state.update { it.copy(find = FindState(query = query)) }
        selectedWebView()?.findAllAsync(query)
    }

    private fun closeFind() {
        selectedWebView()?.clearMatches()
        _state.update { it.copy(find = null) }
    }

    private fun respondPermission(granted: Boolean, remember: Boolean) {
        (state.value.prompt as? BrowserPrompt.Permission)?.prompt?.respond?.invoke(granted, remember)
        clearPrompt()
    }

    private fun respondDownload(accept: Boolean) {
        val prompt = state.value.prompt as? BrowserPrompt.Download
        clearPrompt()
        if (prompt == null || !accept) return
        when (val result = downloadEnqueuer.enqueue(prompt.offer, prompt.isIncognito)) {
            is AppResult.Success -> {
                analytics.track(AnalyticsEvent.DownloadStarted)
                showMessage(BrowserMessage.DOWNLOAD_STARTED)
                if (!prompt.isIncognito) {
                    viewModelScope.launch {
                        downloads.record(
                            result.value.downloadId,
                            prompt.offer.url,
                            result.value.fileName,
                            prompt.offer.mimeType,
                        )
                    }
                }
            }

            is AppResult.Failure -> showMessage(
                when (result.error) {
                    AppError.NotSupported -> BrowserMessage.DOWNLOAD_UNSUPPORTED
                    AppError.Storage -> BrowserMessage.DOWNLOAD_NEEDS_STORAGE
                    else -> BrowserMessage.DOWNLOAD_FAILED
                },
            )
        }
    }

    private fun exitFullscreen() {
        state.value.fullscreen?.callback?.onCustomViewHidden()
        _state.update { it.copy(fullscreen = null) }
    }

    private fun onDataErased(request: EraseRequest) {
        if (!request.tabs) return
        state.value.tabs.toList().forEach { closeTab(it.id) }
    }

    private suspend fun persistTabs() {
        val current = state.value
        val saved = current.tabs.filter { !it.isIncognito }.mapIndexed { index, tab ->
            TabEntity(
                id = tab.id,
                url = tab.url,
                title = tab.title,
                position = index,
                isSelected = tab.id == current.selectedTabId,
                lastAccessedAt = 0,
            )
        }
        tabsRepository.save(saved)
    }

    private fun selectedWebView(): WebView? = state.value.selectedTabId?.let(pool::view)

    private fun bumpGeneration() = _state.update { it.copy(webViewGeneration = it.webViewGeneration + 1) }

    private fun clearPrompt() = _state.update { it.copy(prompt = null) }

    private fun showMessage(message: BrowserMessage) = _state.update { it.copy(message = message) }

    internal fun updateTab(id: String, transform: (BrowserTab) -> BrowserTab) {
        _state.update { current ->
            current.copy(tabs = current.tabs.map { if (it.id == id) transform(it) else it })
        }
    }

    internal fun updateState(transform: (BrowserUiState) -> BrowserUiState) = _state.update(transform)

    internal fun tab(id: String): BrowserTab? = state.value.tabs.firstOrNull { it.id == id }

    /** Incognito must leave no trace, so this is the single gate in front of the history table. */
    internal fun recordVisit(tab: BrowserTab, url: String) {
        if (tab.isIncognito) return
        viewModelScope.launch { history.recordVisit(url, tab.title.ifEmpty { url }) }
    }

    internal fun recordTitle(tab: BrowserTab, url: String, title: String) {
        if (tab.isIncognito) return
        viewModelScope.launch { history.updateTitle(url, title) }
    }

    internal fun saveTabsSoon() {
        viewModelScope.launch { persistTabs() }
    }

    internal fun requestNewWindow(message: android.os.Message, isIncognito: Boolean) {
        val tab = BrowserTab(id = UUID.randomUUID().toString(), url = BLANK_URL, isIncognito = isIncognito)
        _state.update { it.copy(tabs = it.tabs + tab, selectedTabId = tab.id, isTabSwitcherOpen = false) }
        viewModelScope.launch {
            val webView = pool.acquire(specFor(tab), TabEventsBridge(this@BrowserViewModel, tab.id)) { }
            (message.obj as? WebView.WebViewTransport)?.webView = webView
            message.sendToTarget()
            bumpGeneration()
        }
    }

    internal fun onRendererGone(id: String) {
        val isVisible = state.value.selectedTabId == id
        viewModelScope.launch {
            if (isVisible) {
                showTab(id)
                showMessage(BrowserMessage.PAGE_RELOADED)
            }
        }
    }

    internal fun fileNameFor(offer: com.seasaw.theta.core.webview.DownloadOffer): String =
        URLUtil.guessFileName(offer.url, offer.contentDisposition, offer.mimeType)

    private companion object {
        const val BLANK_URL = "about:blank"
    }
}
