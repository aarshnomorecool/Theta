package com.seasaw.theta.feature.browser

import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import com.seasaw.theta.core.webview.DownloadOffer
import com.seasaw.theta.core.webview.ExternalLink
import com.seasaw.theta.core.webview.FileChooserRequest
import com.seasaw.theta.core.webview.PageError
import com.seasaw.theta.core.webview.PermissionPrompt
import com.seasaw.theta.core.webview.TabEvents

/** Turns what one tab's WebView reports into state changes. */
internal class TabEventsBridge(private val viewModel: BrowserViewModel, private val tabId: String) : TabEvents {
    private fun isPromptFree() = viewModel.state.value.prompt == null

    override fun onPageStarted(url: String) = viewModel.updateTab(tabId) { it.copy(progress = 0, error = null) }

    override fun onPageFinished(url: String) {
        viewModel.updateTab(tabId) { it.copy(progress = BrowserTab.PROGRESS_DONE) }
        viewModel.saveTabsSoon()
    }

    override fun onProgress(percent: Int) = viewModel.updateTab(tabId) { it.copy(progress = percent) }

    override fun onTitle(title: String) {
        viewModel.updateTab(tabId) { it.copy(title = title) }
        viewModel.tab(tabId)?.let { viewModel.recordTitle(it, it.url, title) }
    }

    override fun onFavicon(icon: Bitmap) = Unit

    override fun onNavigated(url: String, canGoBack: Boolean, canGoForward: Boolean) {
        viewModel.updateTab(tabId) { it.copy(url = url, canGoBack = canGoBack, canGoForward = canGoForward) }
        viewModel.tab(tabId)?.let { viewModel.recordVisit(it, url) }
    }

    override fun onPageError(error: PageError) = viewModel.updateTab(tabId) { it.copy(error = error) }

    /** One question at a time, and only from the tab on screen: a background page must not interrupt the user. */
    private fun ask(prompt: BrowserPrompt, onSkipped: () -> Unit = {}) {
        val isVisibleTab = viewModel.state.value.selectedTabId == tabId
        if (isVisibleTab && isPromptFree()) {
            viewModel.updateState { it.copy(prompt = prompt) }
        } else {
            onSkipped()
        }
    }

    override fun onExternalLink(link: ExternalLink) = ask(BrowserPrompt.OpenExternally(link))

    override fun onPermissionPrompt(prompt: PermissionPrompt) =
        ask(BrowserPrompt.Permission(prompt)) { prompt.respond(false, false) }

    override fun onDownloadOffer(offer: DownloadOffer) {
        val isIncognito = viewModel.tab(tabId)?.isIncognito == true
        ask(BrowserPrompt.Download(offer, viewModel.fileNameFor(offer), isIncognito))
    }

    override fun onNewWindowRequested(resultMessage: Message): Boolean {
        viewModel.requestNewWindow(resultMessage, viewModel.tab(tabId)?.isIncognito == true)
        return true
    }

    override fun onCloseRequested() = viewModel.onEvent(BrowserUiEvent.CloseTab(tabId))

    override fun onFileChooser(request: FileChooserRequest) = viewModel.updateState { it.copy(fileChooser = request) }

    override fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) =
        viewModel.updateState { it.copy(fullscreen = FullscreenContent(view, callback)) }

    override fun onExitFullscreen() = viewModel.updateState { it.copy(fullscreen = null) }

    override fun onFindResult(activeMatch: Int, totalMatches: Int) = viewModel.updateState { state ->
        state.copy(find = state.find?.copy(activeMatch = activeMatch, totalMatches = totalMatches))
    }

    override fun onRendererGone() = viewModel.onRendererGone(tabId)
}
