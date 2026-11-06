package com.seasaw.theta.core.webview

import android.annotation.SuppressLint
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** Applies the security settings from the project rules. Nothing else may configure these. */
internal object LockedWebSettings {
    @SuppressLint("SetJavaScriptEnabled") // A browser that cannot run JavaScript cannot load the modern web.
    fun apply(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.safeBrowsingEnabled = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
        }

        // Pages may only open new windows from a user gesture. See ThetaWebChromeClient.onCreateWindow.
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(true)
        settings.mediaPlaybackRequiresUserGesture = true

        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
    }
}

/**
 * Service workers run with their own settings object and their requests skip WebViewClient, so without this they would
 * escape both the file-access lockdown and the ad blocker. Each WebView profile has its own controller, so this runs
 * once for the default profile and once for every incognito profile.
 */
internal object ServiceWorkerLockdown {
    fun applyToDefaultProfile(intercept: (WebResourceRequest) -> WebResourceResponse?) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) return
        val controller = ServiceWorkerControllerCompat.getInstance()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_FILE_ACCESS)) {
            controller.serviceWorkerWebSettings.allowFileAccess = false
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_CONTENT_ACCESS)) {
            controller.serviceWorkerWebSettings.allowContentAccess = false
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
            controller.setServiceWorkerClient(
                object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                        intercept(request)
                },
            )
        }
    }

    fun applyTo(controller: ServiceWorkerController, intercept: (WebResourceRequest) -> WebResourceResponse?) {
        controller.serviceWorkerWebSettings.allowFileAccess = false
        controller.serviceWorkerWebSettings.allowContentAccess = false
        controller.setServiceWorkerClient(
            object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                    intercept(request)
            },
        )
    }
}

/** Per-tab preferences that the user controls, unlike [LockedWebSettings]. */
data class TabPreferences(
    val darkMode: Boolean = false,
    val textZoomPercent: Int = 100,
    val desktopMode: Boolean = false,
    val liteMode: Boolean = false,
)

internal object UserAgents {
    private val ANDROID_PLATFORM = Regex("\\(Linux; Android [^)]*\\)")
    private const val DESKTOP_PLATFORM = "(X11; Linux x86_64)"

    /** Keeps the real engine version, because sites feature-detect on it, and removes only the mobile markers. */
    fun desktop(mobileUserAgent: String): String = mobileUserAgent
        .replace(ANDROID_PLATFORM, DESKTOP_PLATFORM)
        .replace(" Mobile ", " ")
        .replace(" Version/4.0", "")
}

internal fun WebSettings.applyPreferences(preferences: TabPreferences, mobileUserAgent: String) {
    textZoom = preferences.textZoomPercent
    userAgentString = if (preferences.desktopMode) UserAgents.desktop(mobileUserAgent) else mobileUserAgent
    loadsImagesAutomatically = !preferences.liteMode
    blockNetworkImage = preferences.liteMode
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, preferences.darkMode)
    }
}
