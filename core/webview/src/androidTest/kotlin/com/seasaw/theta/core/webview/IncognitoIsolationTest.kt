package com.seasaw.theta.core.webview

import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The launch-time deletion of a killed process's profile cannot run here, because it needs a second process. It was
 * verified by hand with two `am instrument` runs: after the first process died its Cookies and Local Storage were on
 * disk, and in the second process deleteProfile returned true and the profile directory was gone.
 */
@RunWith(AndroidJUnit4::class)
class IncognitoIsolationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profiles = IncognitoProfiles(context, WebDataCleaner(context))
    private val site = "https://isolation.example"

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Before
    fun requireMultiProfile() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    }

    private fun startSession(): WebView {
        val view = WebView(context)
        profiles.attach(view)
        return view
    }

    @Test
    fun incognitoCookiesAreIsolatedFromTheRegularProfile() {
        onMain {
            val view = startSession()
            val incognitoCookies = profiles.cookiesFor(isIncognito = true)
            incognitoCookies.setCookie(site, "private=1")
            incognitoCookies.flush()

            assertTrue(incognitoCookies.getCookie(site).orEmpty().contains("private=1"))
            assertFalse(CookieManager.getInstance().getCookie(site).orEmpty().contains("private=1"))
            view.destroy()
            profiles.wipeAfterSession()
        }
    }

    /** Regression test: the original design reused one profile, so its undeletable data reached the next session. */
    @Test
    fun aNewSessionNeverSeesThePreviousSessionsCookies() {
        onMain {
            val first = startSession()
            profiles.cookiesFor(isIncognito = true).setCookie(site, "private=1")
            first.destroy()
            profiles.wipeAfterSession()

            val second = startSession()
            assertNull(profiles.cookiesFor(isIncognito = true).getCookie(site))
            second.destroy()
            profiles.wipeAfterSession()
        }
    }

    /** Layer 1 of the guarantee: the data goes when the session ends, not only when the directory is deleted later. */
    @Test
    fun endingASessionClearsItsCookiesFromTheProfile() {
        var name = ""
        onMain {
            val before = profiles.remainingProfiles().toSet()
            val view = startSession()
            profiles.cookiesFor(isIncognito = true).setCookie(site, "private=1")
            name = (profiles.remainingProfiles() - before).single()
            view.destroy()
            profiles.wipeAfterSession()
        }
        val deadline = SystemClock.elapsedRealtime() + WIPE_TIMEOUT_MILLIS
        var cookie: String? = "unread"
        while (cookie != null && SystemClock.elapsedRealtime() < deadline) {
            onMain { cookie = ProfileStore.getInstance().getProfile(name)?.cookieManager?.getCookie(site) }
            if (cookie != null) SystemClock.sleep(POLL_MILLIS)
        }
        assertNull(cookie)
    }

    @Test
    fun launchSweepKeepsRegularCookiesAndToleratesProfilesLoadedInThisProcess() {
        onMain {
            CookieManager.getInstance().setCookie(site, "regular=1")
            val view = startSession()
            view.destroy()
            profiles.wipeAfterSession()

            profiles.wipeStaleProfiles()

            assertTrue(CookieManager.getInstance().getCookie(site).orEmpty().contains("regular=1"))
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    @Test
    fun launchSweepLeavesAnActiveSessionAlone() {
        onMain {
            val view = startSession()
            profiles.cookiesFor(isIncognito = true).setCookie(site, "private=1")
            profiles.wipeStaleProfiles()
            assertEquals("private=1", profiles.cookiesFor(isIncognito = true).getCookie(site))
            view.destroy()
            profiles.wipeAfterSession()
        }
    }

    @Test
    fun wipingWithNoSessionIsHarmless() {
        onMain {
            profiles.wipeStaleProfiles()
            profiles.wipeAfterSession()
        }
    }

    private companion object {
        const val WIPE_TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 100L
    }
}
