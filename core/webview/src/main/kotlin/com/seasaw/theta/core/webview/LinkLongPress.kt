package com.seasaw.theta.core.webview

import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/** Turns a long press on a link into [TabEvents.onLinkLongPressed]. Anything else keeps WebView's own behaviour. */
internal object LinkLongPress {
    fun handle(webView: WebView, events: TabEvents): Boolean {
        val hit = webView.hitTestResult
        return when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> report(hit.extra, events)

            // For a linked image, the hit result holds the image address; the link has to be asked for separately.
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val reply = Handler(Looper.getMainLooper()) { message ->
                    report(message.data.getString("url"), events)
                    true
                }.obtainMessage()
                webView.requestFocusNodeHref(reply)
                true
            }

            else -> false
        }
    }

    private fun report(url: String?, events: TabEvents): Boolean {
        val isWebLink = url != null && (url.startsWith("https://") || url.startsWith("http://"))
        if (isWebLink) events.onLinkLongPressed(url)
        return isWebLink
    }
}
