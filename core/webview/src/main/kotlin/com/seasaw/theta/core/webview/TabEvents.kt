package com.seasaw.theta.core.webview

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/** Why a page could not be shown. The browser screen renders these natively, not as HTML inside the WebView. */
sealed interface PageError {
    val url: String

    /** The certificate failed. The connection has already been cancelled and there is no way to proceed. */
    data class Ssl(override val url: String) : PageError

    /** Offline, DNS failure, timeout and so on. */
    data class Network(override val url: String, val errorCode: Int) : PageError
}

/** A site asked for camera, microphone or location and has no remembered answer. */
class PermissionPrompt(
    val host: String,
    val permissions: Set<SitePermission>,
    /** May only be called once. [remember] is ignored for incognito tabs. */
    val respond: (granted: Boolean, remember: Boolean) -> Unit,
)

data class DownloadOffer(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long,
)

class FileChooserRequest(val params: WebChromeClient.FileChooserParams, val callback: ValueCallback<Array<Uri>>)

/** What a tab reports to the browser layer. Everything arrives on the main thread. */
@Suppress("TooManyFunctions")
interface TabEvents {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)
    fun onProgress(percent: Int)
    fun onTitle(title: String)
    fun onFavicon(icon: Bitmap)

    /** The address changed, including same-document navigation. [canGoBack] and [canGoForward] are current. */
    fun onNavigated(url: String, canGoBack: Boolean, canGoForward: Boolean)

    fun onPageError(error: PageError)

    /** The page wants to open another app. Show a prompt; never launch without one. */
    fun onExternalLink(link: ExternalLink)

    fun onPermissionPrompt(prompt: PermissionPrompt)
    fun onDownloadOffer(offer: DownloadOffer)

    /** A user gesture asked for a new window. Deliver a WebView through [resultMessage] to accept it. */
    fun onNewWindowRequested(resultMessage: Message): Boolean

    fun onCloseRequested()
    fun onFileChooser(request: FileChooserRequest)
    fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onExitFullscreen()
    fun onFindResult(activeMatch: Int, totalMatches: Int)

    /** The renderer died, usually from memory pressure. This tab's WebView is unusable and has been destroyed. */
    fun onRendererGone()
}
