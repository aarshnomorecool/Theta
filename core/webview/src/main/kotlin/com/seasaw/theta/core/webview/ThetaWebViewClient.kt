package com.seasaw.theta.core.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/** Decides what happens to each intercepted request. Shared by page requests and service worker requests. */
internal class RequestInterceptor(private val blocker: RequestBlocker, private val liteModeEnabled: () -> Boolean) {
    fun intercept(request: WebResourceRequest, pageUrl: String?): WebResourceResponse? {
        val url = request.url.toString()
        if (!url.startsWith("http")) return null
        val headers = request.requestHeaders
        val kind = ResourceClassifier.classify(
            url = url,
            isMainFrame = request.isForMainFrame,
            acceptHeader = headers[HEADER_ACCEPT] ?: headers[HEADER_ACCEPT.lowercase()],
            fetchDest = headers[HEADER_FETCH_DEST] ?: headers[HEADER_FETCH_DEST.lowercase()],
        )
        if (kind == ResourceKind.DOCUMENT) return null
        val isHeavyInLiteMode = kind in LITE_MODE_BLOCKED && liteModeEnabled()
        return if (isHeavyInLiteMode || blocker.shouldBlock(InterceptedRequest(url, pageUrl, kind))) empty() else null
    }

    /** A new stream each time, because a response's stream is consumed by the WebView that receives it. */
    private fun empty() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private companion object {
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_FETCH_DEST = "Sec-Fetch-Dest"
        val LITE_MODE_BLOCKED = setOf(ResourceKind.IMAGE, ResourceKind.MEDIA, ResourceKind.OBJECT)
    }
}

internal class ThetaWebViewClient(
    private val events: TabEvents,
    private val interceptor: RequestInterceptor,
    private val externalLinks: ExternalLinks,
    private val onRendererGone: () -> Unit,
    private val cosmetics: CosmeticFilter = CosmeticFilter.None,
) : WebViewClient() {
    // Written from the main thread and read from WebView IO threads.
    @Volatile
    private var pageUrl: String? = null

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        when (NavigationPolicy.decide(request.url.toString())) {
            NavigationDecision.LoadInWebView -> false

            NavigationDecision.Block -> true

            NavigationDecision.AskToOpenExternally -> {
                // Only top-level navigations may prompt. Otherwise a hidden ad iframe could spam the user with dialogs.
                if (request.isForMainFrame) {
                    externalLinks.parse(request.url.toString())?.let(events::onExternalLink)
                }
                true
            }
        }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // Main-frame requests come first, so subresources that follow see the page they belong to.
        if (request.isForMainFrame) pageUrl = request.url.toString()
        return interceptor.intercept(request, pageUrl)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        pageUrl = url
        events.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        events.onPageFinished(url)
        CosmeticInjector.run(view, cosmetics)
        // Consent pop-ups are often added by a script a moment after load, so look once more a little later.
        view.postDelayed({ if (view.url == url) CosmeticInjector.run(view, cosmetics) }, LATE_PASS_MILLIS)
    }

    /** The first paint: hiding here means most pop-ups never flash on screen. */
    override fun onPageCommitVisible(view: WebView, url: String) = CosmeticInjector.run(view, cosmetics)

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        pageUrl = url
        events.onNavigated(url, view.canGoBack(), view.canGoForward())
    }

    /** Project rule: an SSL error is never bypassable. There is deliberately no proceed path anywhere in this app. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        if (error.url == pageUrl) events.onPageError(PageError.Ssl(error.url))
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            events.onPageError(PageError.Network(request.url.toString(), error.errorCode))
        }
    }

    private companion object {
        const val LATE_PASS_MILLIS = 2_500L
    }

    /** Returning true keeps the app alive. Without an override, a renderer crash takes the whole browser down. */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone()
        events.onRendererGone()
        return true
    }
}
