package com.seasaw.theta.core.webview

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.seasaw.theta.core.common.url.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ThetaWebChromeClient(
    private val events: TabEvents,
    private val permissions: SitePermissionCoordinator,
    private val isIncognito: Boolean,
    private val mainScope: CoroutineScope,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) = events.onProgress(newProgress)

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (title != null) events.onTitle(title)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        if (icon != null) events.onFavicon(icon)
    }

    /** Pages get a new tab only from a user gesture, which is what keeps pop-under ads out. */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean =
        isUserGesture && events.onNewWindowRequested(resultMsg)

    override fun onCloseWindow(window: WebView) = events.onCloseRequested()

    override fun onPermissionRequest(request: PermissionRequest) {
        val host = request.origin.host
        val requested = request.resources.mapNotNull(SitePermission::fromWebResource).toSet()
        // Anything else a page can ask for here (MIDI, protected media ID) is outside the supported set, so deny it.
        if (host == null || requested.isEmpty() || requested.size != request.resources.size) {
            request.deny()
            return
        }
        resolve(host, requested) { granted ->
            if (granted) request.grant(request.resources) else request.deny()
        }
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        val host = UrlUtils.hostOf(origin)
        if (host == null) {
            callback.invoke(origin, false, false)
            return
        }
        // retain is always false: decisions live in our own table, so incognito stays in control of forgetting them.
        resolve(host, setOf(SitePermission.LOCATION)) { granted -> callback.invoke(origin, granted, false) }
    }

    private fun resolve(host: String, requested: Set<SitePermission>, respond: (Boolean) -> Unit) {
        mainScope.launch {
            when (permissions.lookup(host, requested, isIncognito)) {
                PermissionDecision.GRANTED -> respond(true)

                PermissionDecision.DENIED -> respond(false)

                PermissionDecision.ASK -> events.onPermissionPrompt(
                    PermissionPrompt(host, requested) { granted, remember ->
                        respond(granted)
                        if (remember) {
                            mainScope.launch { permissions.remember(host, requested, granted, isIncognito) }
                        }
                    },
                )
            }
        }
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        events.onFileChooser(FileChooserRequest(fileChooserParams, filePathCallback))
        return true
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) = events.onEnterFullscreen(view, callback)

    override fun onHideCustomView() = events.onExitFullscreen()
}
