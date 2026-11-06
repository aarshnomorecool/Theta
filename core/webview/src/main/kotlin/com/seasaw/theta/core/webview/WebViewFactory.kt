package com.seasaw.theta.core.webview

import android.content.Context
import android.content.MutableContextWrapper
import android.view.ContextThemeWrapper
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.seasaw.theta.core.common.ApplicationScope
import com.seasaw.theta.core.common.MainDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** The only place a WebView is created or configured. */
@Singleton
class WebViewFactory @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val blocker: RequestBlocker,
    private val externalLinks: ExternalLinks,
    private val permissions: SitePermissionCoordinator,
    private val incognito: IncognitoProfiles,
    @ApplicationScope private val appScope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {
    @Volatile
    private var liteMode = false
    private val serviceWorkersLocked = AtomicBoolean(false)

    private val interceptor = RequestInterceptor(blocker) { liteMode }

    fun setLiteMode(enabled: Boolean) {
        liteMode = enabled
    }

    /**
     * @param onRendererGone tells the owner to discard this WebView, because a dead renderer cannot be reused
     */
    fun create(
        isIncognito: Boolean,
        preferences: TabPreferences,
        events: TabEvents,
        onRendererGone: () -> Unit,
    ): WebView {
        lockServiceWorkersOnce()
        // The MutableContextWrapper lets the WebView follow Activity recreation without holding a stale Activity.
        val context = MutableContextWrapper(themed(appContext, preferences.darkMode))
        val webView = WebView(context)
        // Has to be first: a profile cannot be assigned once the WebView has started using one.
        if (isIncognito) {
            incognito.attach(webView) { controller ->
                ServiceWorkerLockdown.applyTo(controller) { request -> interceptor.intercept(request, pageUrl = null) }
            }
        }

        LockedWebSettings.apply(webView.settings)
        webView.settings.applyPreferences(preferences, WebSettings.getDefaultUserAgent(appContext))

        webView.webViewClient = ThetaWebViewClient(events, interceptor, externalLinks, onRendererGone)
        webView.webChromeClient = ThetaWebChromeClient(
            events = events,
            permissions = permissions,
            isIncognito = isIncognito,
            mainScope = CoroutineScope(appScope.coroutineContext + mainDispatcher),
        )
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            events.onDownloadOffer(DownloadOffer(url, userAgent, contentDisposition, mimeType, contentLength))
        }
        webView.setFindListener { activeMatch, totalMatches, isDone ->
            if (isDone) events.onFindResult(activeMatch, totalMatches)
        }
        return webView
    }

    /** Applies changed preferences to a live WebView. Dark mode needs a new WebView instead, see [WebViewPool]. */
    fun update(webView: WebView, preferences: TabPreferences) {
        webView.settings.applyPreferences(preferences, WebSettings.getDefaultUserAgent(appContext))
    }

    /** Points the WebView's context at [host], or back at the application when [host] is null. */
    fun attachHost(webView: WebView, host: Context?, darkMode: Boolean) {
        (webView.context as? MutableContextWrapper)?.baseContext = themed(host ?: appContext, darkMode)
    }

    private fun themed(base: Context, darkMode: Boolean): Context =
        ContextThemeWrapper(base, if (darkMode) R.style.ThetaWebViewDark else R.style.ThetaWebViewLight)

    private fun lockServiceWorkersOnce() {
        if (serviceWorkersLocked.compareAndSet(false, true)) {
            ServiceWorkerLockdown.applyToDefaultProfile { request -> interceptor.intercept(request, pageUrl = null) }
        }
    }

    /** The version of the system WebView, for diagnostics. Null when none is installed. */
    fun webViewVersion(): String? = if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        WebViewCompat.getCurrentWebViewPackage(appContext)?.versionName
    } else {
        null
    }
}
