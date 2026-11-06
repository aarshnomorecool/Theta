package com.seasaw.theta.feature.browser

import android.view.View
import android.webkit.WebChromeClient
import com.seasaw.theta.core.webview.DownloadOffer
import com.seasaw.theta.core.webview.ExternalLink
import com.seasaw.theta.core.webview.FileChooserRequest
import com.seasaw.theta.core.webview.PageError
import com.seasaw.theta.core.webview.PermissionPrompt

/** One open tab. An empty [url] means the new-tab page, which is native and needs no WebView. */
data class BrowserTab(
    val id: String,
    val url: String = "",
    val title: String = "",
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val progress: Int = PROGRESS_DONE,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: PageError? = null,
) {
    val isNewTab: Boolean get() = url.isEmpty()
    val isLoading: Boolean get() = progress in 0 until PROGRESS_DONE

    companion object {
        const val PROGRESS_DONE = 100
    }
}

/** A question for the user. Only one shows at a time, so a page cannot stack dialogs. */
sealed interface BrowserPrompt {
    data class Permission(val prompt: PermissionPrompt) : BrowserPrompt
    data class OpenExternally(val link: ExternalLink) : BrowserPrompt
    data class Download(val offer: DownloadOffer, val fileName: String, val isIncognito: Boolean) : BrowserPrompt
}

/** Something brief to tell the user. Text lives with the UI so that it follows the current language. */
enum class BrowserMessage {
    DOWNLOAD_STARTED,
    DOWNLOAD_FAILED,
    DOWNLOAD_UNSUPPORTED,
    DOWNLOAD_NEEDS_STORAGE,
    NO_APP_FOUND,
    PAGE_RELOADED,
}

data class FindState(val query: String = "", val activeMatch: Int = 0, val totalMatches: Int = 0)

/** A page's video in fullscreen. Holds a View, because that is what WebView hands over. */
class FullscreenContent(val view: View, val callback: WebChromeClient.CustomViewCallback)

data class BrowserUiState(
    val tabs: List<BrowserTab> = emptyList(),
    val selectedTabId: String? = null,
    val isTabSwitcherOpen: Boolean = false,
    val isBookmarked: Boolean = false,
    val isAdBlockEnabled: Boolean = true,
    val isSiteExemptFromAdBlock: Boolean = false,
    val isLiteMode: Boolean = false,
    val textZoomPercent: Int = 100,
    val isIncognitoIsolationSupported: Boolean = true,
    val find: FindState? = null,
    val prompt: BrowserPrompt? = null,
    val message: BrowserMessage? = null,
    val fullscreen: FullscreenContent? = null,
    val fileChooser: FileChooserRequest? = null,
    /** Changes whenever the visible tab's WebView instance changes, which tells the UI to attach the new one. */
    val webViewGeneration: Int = 0,
) {
    val selectedTab: BrowserTab? get() = tabs.firstOrNull { it.id == selectedTabId }
}

sealed interface BrowserUiEvent {
    data class Submit(val text: String) : BrowserUiEvent
    data class OpenUrl(val url: String) : BrowserUiEvent
    data class NewTab(val isIncognito: Boolean) : BrowserUiEvent
    data class SelectTab(val id: String) : BrowserUiEvent
    data class CloseTab(val id: String) : BrowserUiEvent
    data object CloseIncognitoTabs : BrowserUiEvent
    data object GoBack : BrowserUiEvent
    data object GoForward : BrowserUiEvent
    data object GoHome : BrowserUiEvent
    data object Reload : BrowserUiEvent
    data object StopLoading : BrowserUiEvent
    data class ShowTabSwitcher(val visible: Boolean) : BrowserUiEvent
    data object ToggleBookmark : BrowserUiEvent
    data object AddToSpeedDial : BrowserUiEvent
    data object ToggleSiteAdBlock : BrowserUiEvent
    data object ToggleDesktopMode : BrowserUiEvent
    data object ToggleLiteMode : BrowserUiEvent
    data class SetTextZoom(val percent: Int) : BrowserUiEvent
    data class ThemeChanged(val isDark: Boolean) : BrowserUiEvent
    data object StartFind : BrowserUiEvent
    data class FindQuery(val query: String) : BrowserUiEvent
    data class FindStep(val forward: Boolean) : BrowserUiEvent
    data object CloseFind : BrowserUiEvent
    data class RespondPermission(val granted: Boolean, val remember: Boolean) : BrowserUiEvent
    data class RespondOpenExternally(val open: Boolean) : BrowserUiEvent
    data class RespondDownload(val accept: Boolean) : BrowserUiEvent
    data object ExitFullscreen : BrowserUiEvent
    data object FileChooserHandled : BrowserUiEvent
    data object MessageShown : BrowserUiEvent
    data object TrimMemory : BrowserUiEvent
    data object AppBackgrounded : BrowserUiEvent
}

/** Pure tab list rules, kept separate so that they can be unit tested without WebViews. */
internal object TabListRules {
    /** Which tab to show after closing [closedId]: its right neighbour, else its left, staying in the same mode. */
    fun nextSelection(tabs: List<BrowserTab>, closedId: String): String? {
        val index = tabs.indexOfFirst { it.id == closedId }
        if (index < 0) return tabs.firstOrNull()?.id
        val closedIsIncognito = tabs[index].isIncognito
        val remaining = tabs.filterIndexed { i, _ -> i != index }
        val sameMode = remaining.filter { it.isIncognito == closedIsIncognito }
        val right = sameMode.firstOrNull { tabs.indexOf(it) > index }
        val left = sameMode.lastOrNull { tabs.indexOf(it) < index }
        return (right ?: left ?: remaining.lastOrNull())?.id
    }

    /** A new-tab page that is already open and empty gets reused, so repeated taps do not pile up blank tabs. */
    fun reusableNewTab(tabs: List<BrowserTab>, isIncognito: Boolean): BrowserTab? =
        tabs.firstOrNull { it.isNewTab && it.isIncognito == isIncognito }
}
