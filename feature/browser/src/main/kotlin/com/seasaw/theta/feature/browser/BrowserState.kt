package com.seasaw.theta.feature.browser

import android.graphics.Bitmap
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
    val groupId: String? = null,
    /** The site's icon, when it has sent one. Never written to disk for incognito tabs. */
    val favicon: Bitmap? = null,
) {
    val isNewTab: Boolean get() = url.isEmpty()
    val isLoading: Boolean get() = progress in 0 until PROGRESS_DONE

    companion object {
        const val PROGRESS_DONE = 100
    }
}

/** A named, coloured set of tabs. Private tabs only ever join private groups, and the other way round. */
data class TabGroup(val id: String, val name: String, val colorIndex: Int, val isIncognito: Boolean = false)

/** Where a long-pressed link should open. */
enum class LinkTarget { NEW_TAB, NEW_TAB_IN_GROUP, INCOGNITO_TAB }

/** A question for the user. Only one shows at a time, so a page cannot stack dialogs. */
sealed interface BrowserPrompt {
    data class Permission(val prompt: PermissionPrompt) : BrowserPrompt
    data class OpenExternally(val link: ExternalLink) : BrowserPrompt

    /** [pageUrl] is the page that offered the file, which decides whether its site's cookies may go along. */
    data class Download(
        val offer: DownloadOffer,
        val fileName: String,
        val isIncognito: Boolean,
        val pageUrl: String? = null,
    ) : BrowserPrompt
}

/** Something brief to tell the user. Text lives with the UI so that it follows the current language. */
enum class BrowserMessage {
    DOWNLOAD_STARTED,
    DOWNLOAD_FAILED,
    DOWNLOAD_UNSUPPORTED,
    DOWNLOAD_NEEDS_STORAGE,
    NO_APP_FOUND,
    PAGE_RELOADED,
    OPENED_IN_BACKGROUND,
    LINK_COPIED,
}

data class FindState(val query: String = "", val activeMatch: Int = 0, val totalMatches: Int = 0)

/** A page's video in fullscreen. Holds a View, because that is what WebView hands over. */
class FullscreenContent(val view: View, val callback: WebChromeClient.CustomViewCallback)

data class BrowserUiState(
    val tabs: List<BrowserTab> = emptyList(),
    val groups: List<TabGroup> = emptyList(),
    val selectedTabId: String? = null,
    /** Pictures of tabs for the switcher, by tab id. Private tabs' pictures exist only here, in memory. */
    val previews: Map<String, Bitmap> = emptyMap(),
    val isTabPreviewsEnabled: Boolean = true,
    val showZoomButtons: Boolean = false,
    /** A link the user long-pressed, waiting for them to pick what to do with it. */
    val linkMenuUrl: String? = null,
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

    fun group(id: String?): TabGroup? = id?.let { wanted -> groups.firstOrNull { it.id == wanted } }
}

sealed interface BrowserUiEvent {
    data class Submit(val text: String) : BrowserUiEvent
    data class OpenUrl(val url: String) : BrowserUiEvent
    data class NewTab(val isIncognito: Boolean, val groupId: String? = null) : BrowserUiEvent
    data class SelectTab(val id: String) : BrowserUiEvent
    data class CloseTab(val id: String) : BrowserUiEvent
    data class CloseTabs(val ids: Set<String>) : BrowserUiEvent
    data class CloseAllTabs(val isIncognito: Boolean) : BrowserUiEvent

    /** Puts [tabIds] in a new group. With no tabs, the group starts with a fresh new-tab page. */
    data class CreateGroup(val tabIds: Set<String>, val name: String, val colorIndex: Int, val isIncognito: Boolean) :
        BrowserUiEvent

    data class AddToGroup(val tabIds: Set<String>, val groupId: String) : BrowserUiEvent
    data class RemoveFromGroup(val tabIds: Set<String>) : BrowserUiEvent
    data class EditGroup(val id: String, val name: String, val colorIndex: Int) : BrowserUiEvent
    data class Ungroup(val id: String) : BrowserUiEvent
    data class CloseGroup(val id: String) : BrowserUiEvent
    data class OpenLink(val url: String, val target: LinkTarget) : BrowserUiEvent
    data object DismissLinkMenu : BrowserUiEvent
    data object LinkCopied : BrowserUiEvent

    /** A picture of the tab's page, taken by the UI just before the page leaves the screen. */
    data class PreviewCaptured(val tabId: String, val preview: Bitmap) : BrowserUiEvent
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
    fun reusableNewTab(tabs: List<BrowserTab>, isIncognito: Boolean, groupId: String? = null): BrowserTab? =
        tabs.firstOrNull { it.isNewTab && it.isIncognito == isIncognito && it.groupId == groupId }
}

/** Pure tab group rules. Groups keep their tabs side by side, which is what makes them readable in the switcher. */
internal object TabGroupRules {
    const val COLOR_COUNT = 9

    /** The first colour no group uses yet, so new groups are easy to tell apart. */
    fun nextColor(groups: List<TabGroup>): Int =
        (0 until COLOR_COUNT).firstOrNull { color -> groups.none { it.colorIndex == color } }
            ?: (groups.size % COLOR_COUNT)

    /** Groups with no tabs left disappear, as in other browsers. */
    fun prune(tabs: List<BrowserTab>, groups: List<TabGroup>): List<TabGroup> =
        groups.filter { group -> tabs.any { it.groupId == group.id } }

    /**
     * Moves [ids] into [group], after the group's last tab, or where the first of them was if the group is new. Tabs
     * of the other mode are left alone: a private tab never joins a regular group.
     */
    fun moveIntoGroup(tabs: List<BrowserTab>, ids: Set<String>, group: TabGroup): List<BrowserTab> {
        val moving = tabs.filter { it.id in ids && it.isIncognito == group.isIncognito }
        if (moving.isEmpty()) return tabs
        val movingIds = moving.map { it.id }.toSet()
        val rest = tabs.filter { it.id !in movingIds }
        val lastOfGroup = rest.indexOfLast { it.groupId == group.id }
        val insertAt = if (lastOfGroup >= 0) {
            lastOfGroup + 1
        } else {
            val firstMoving = tabs.indexOfFirst { it.id in movingIds }
            rest.count { tabs.indexOf(it) < firstMoving }
        }
        return rest.take(insertAt) + moving.map { it.copy(groupId = group.id) } + rest.drop(insertAt)
    }

    /** Where a new tab in [groupId] goes: right after the group's last tab, else at the end. */
    fun insertionIndex(tabs: List<BrowserTab>, groupId: String?): Int {
        if (groupId == null) return tabs.size
        val last = tabs.indexOfLast { it.groupId == groupId }
        return if (last < 0) tabs.size else last + 1
    }
}
