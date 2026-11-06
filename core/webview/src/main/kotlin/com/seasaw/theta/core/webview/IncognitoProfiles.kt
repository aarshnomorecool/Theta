package com.seasaw.theta.core.webview

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ServiceWorkerController
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Isolates incognito tabs from regular browsing, using a separate WebView profile where the device supports it.
 *
 * WebView refuses to delete a profile that has been loaded in the current process, even after its last WebView is
 * destroyed, so a profile's directory can only ever be removed on the next launch. The privacy guarantee therefore has
 * three layers:
 * 1. Ending a session clears its cookies, storage and cache immediately through the profile's own APIs.
 * 2. Each session gets a new, uniquely named profile, so it cannot see the data of any earlier session.
 * 3. [wipeStaleProfiles] deletes the leftover directories on the next launch, which also covers a process that the
 *    system killed before layer 1 could run.
 *
 * Main thread only, like the WebViews it serves.
 */
@Singleton
class IncognitoProfiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataCleaner: WebDataCleaner,
) {
    private var sessionProfile: String? = null

    /**
     * False on old WebView versions. Incognito then shares the default profile's data, and closing it wipes everything.
     * The UI must show a notice in that case, because it also signs the user out of their regular sites.
     */
    val isIsolationSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    /**
     * Must run before the WebView loads anything. A profile cannot be changed afterwards.
     *
     * @param configureNewProfile receives the service worker controller of a profile being created this call
     */
    fun attach(webView: WebView, configureNewProfile: (ServiceWorkerController) -> Unit = {}) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        val existing = sessionProfile
        val name = existing ?: "$PROFILE_PREFIX${UUID.randomUUID()}".also { sessionProfile = it }
        val profile = ProfileStore.getInstance().getOrCreateProfile(name)
        if (existing == null) configureNewProfile(profile.serviceWorkerController)
        WebViewCompat.setProfile(webView, name)
    }

    fun cookiesFor(isIncognito: Boolean): CookieManager {
        val name = sessionProfile
        if (isIncognito && name != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getProfile(name)?.let { return it.cookieManager }
        }
        return CookieManager.getInstance()
    }

    /** Wipes a private session that has just ended. Call only after every incognito WebView is destroyed. */
    fun wipeAfterSession() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            dataCleaner.clearWebData()
            return
        }
        val name = sessionProfile ?: return
        sessionProfile = null
        val profile = ProfileStore.getInstance().getProfile(name) ?: return
        profile.cookieManager.removeAllCookies(null)
        profile.cookieManager.flush()
        profile.webStorage.deleteAllData()
        profile.geolocationPermissions.clearAll()
        // clearCache only exists on WebView, so a short-lived one is attached to reach this profile's cache.
        WebView(context).apply {
            WebViewCompat.setProfile(this, name)
            clearCache(true)
            clearFormData()
            destroy()
        }
    }

    /**
     * Deletes the directories of private profiles from earlier processes. Run on every launch, but after the first
     * frame because it initialises the WebView engine.
     *
     * Profiles loaded in this process are skipped rather than failing: their data is gone already, and their
     * directories go on the next launch. There is no default-profile wipe here: without isolation the default profile
     * holds the user's regular logins, and we cannot tell whether an incognito session was involved.
     */
    fun wipeStaleProfiles() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        val store = ProfileStore.getInstance()
        store.allProfileNames
            .filter { it.startsWith(PROFILE_PREFIX) && it != sessionProfile }
            .forEach { name ->
                try {
                    store.deleteProfile(name)
                } catch (ignored: IllegalStateException) {
                    // Loaded in this process, so the next launch gets it. See the class comment.
                }
            }
    }

    /** Names of private profiles still on disk. Exposed for tests of the wipe guarantee. */
    internal fun remainingProfiles(): List<String> {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return emptyList()
        return ProfileStore.getInstance().allProfileNames.filter { it.startsWith(PROFILE_PREFIX) }
    }

    private companion object {
        const val PROFILE_PREFIX = "theta_incognito_"
    }
}

/** Removes everything the WebView stores in the default profile. */
@Singleton
class WebDataCleaner @Inject constructor(@ApplicationContext private val context: Context) {
    fun clearWebData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        GeolocationPermissions.getInstance().clearAll()
        // clearCache is an instance method, but the cache it clears is shared by the whole profile.
        WebView(context).apply {
            clearCache(true)
            clearFormData()
            destroy()
        }
    }
}
