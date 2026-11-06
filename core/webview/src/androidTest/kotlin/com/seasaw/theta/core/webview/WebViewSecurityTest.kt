package com.seasaw.theta.core.webview

import android.os.Message
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises real WebViews against a local server: the security rules are behaviour of the platform's engine, so they
 * cannot be proved with mocks. MockK is avoided here so that the assertions run the same code production runs.
 */
@RunWith(AndroidJUnit4::class)
class WebViewSecurityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var server: MockWebServer
    private var webView: WebView? = null

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { webView?.destroy() }
        server.close()
    }

    private open class RecordingEvents : TabEvents {
        val finished = CountDownLatch(1)
        val errors = mutableListOf<PageError>()
        val errorSeen = CountDownLatch(1)
        val externalLinks = mutableListOf<ExternalLink>()
        val externalSeen = CountDownLatch(1)

        override fun onPageStarted(url: String) = Unit
        override fun onPageFinished(url: String) = finished.countDown()
        override fun onProgress(percent: Int) = Unit
        override fun onTitle(title: String) = Unit
        override fun onFavicon(icon: android.graphics.Bitmap) = Unit
        override fun onNavigated(url: String, canGoBack: Boolean, canGoForward: Boolean) = Unit
        override fun onPageError(error: PageError) {
            errors += error
            errorSeen.countDown()
        }

        override fun onExternalLink(link: ExternalLink) {
            externalLinks += link
            externalSeen.countDown()
        }

        override fun onPermissionPrompt(prompt: PermissionPrompt) = Unit
        override fun onDownloadOffer(offer: DownloadOffer) = Unit
        override fun onNewWindowRequested(resultMessage: Message) = false
        override fun onCloseRequested() = Unit
        override fun onFileChooser(request: FileChooserRequest) = Unit
        override fun onEnterFullscreen(
            view: android.view.View,
            callback: android.webkit.WebChromeClient.CustomViewCallback,
        ) = Unit

        override fun onExitFullscreen() = Unit
        override fun onFindResult(activeMatch: Int, totalMatches: Int) = Unit
        override fun onRendererGone() = Unit
    }

    private fun newWebView(events: TabEvents, blocker: RequestBlocker = RequestBlocker { false }): WebView {
        var created: WebView? = null
        instrumentation.runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            created = WebView(context).also { view ->
                LockedWebSettings.apply(view.settings)
                view.webViewClient = ThetaWebViewClient(
                    events = events,
                    interceptor = RequestInterceptor(blocker) { false },
                    externalLinks = ExternalLinks(context),
                    onRendererGone = {},
                )
            }
        }
        webView = created
        return checkNotNull(created)
    }

    @Test
    fun lockedSettingsAreApplied() {
        val view = newWebView(RecordingEvents())
        instrumentation.runOnMainSync {
            val settings = view.settings
            assertFalse(settings.allowFileAccess)
            assertFalse(settings.allowContentAccess)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.mixedContentMode)
            assertTrue(settings.safeBrowsingEnabled)
            assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
        }
    }

    @Test
    fun badCertificateIsCancelledAndReported() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.enqueue(MockResponse.Builder().body("<html>should never render</html>").build())
        server.start()

        val events = RecordingEvents()
        val view = newWebView(events)
        val url = server.url("/").toString()
        instrumentation.runOnMainSync { view.loadUrl(url) }

        assertTrue("SSL error was not reported", events.errorSeen.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue(events.errors.single() is PageError.Ssl)
        assertEquals("The page must never be served after an SSL error", 0, server.requestCount)
    }

    @Test
    fun blockedSubresourcesNeverReachTheNetwork() {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/html")
                .body("""<html><body><img src="/ad.png"><img src="/ok.png"></body></html>""")
                .build(),
        )
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(404).build())
        server.start()

        val events = RecordingEvents()
        val view = newWebView(events, RequestBlocker { it.url.endsWith("/ad.png") })
        val url = server.url("/page").toString()
        instrumentation.runOnMainSync { view.loadUrl(url) }
        assertTrue(events.finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val paths = (0 until server.requestCount).map { checkNotNull(server.takeRequest().url).encodedPath }
        assertTrue(paths.toString(), "/page" in paths)
        assertFalse(paths.toString(), "/ad.png" in paths)
    }

    @Test
    fun externalLinksPromptInsteadOfLaunching() {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/html")
                .body("""<html><script>location.href='market://details?id=x'</script></html>""")
                .build(),
        )
        server.start()

        val events = RecordingEvents()
        val view = newWebView(events)
        val url = server.url("/").toString()
        instrumentation.runOnMainSync {
            view.settings.javaScriptEnabled = true
            view.loadUrl(url)
        }
        assertTrue("Prompt was not requested", events.externalSeen.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals("market", events.externalLinks.single().scheme)
    }

    @Test
    fun maliciousIntentIsSanitised() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val url = "intent://x#Intent;scheme=y;component=com.victim/.Secret;" +
            "launchFlags=0x1;S.browser_fallback_url=http%3A%2F%2Fevil.example;end"
        val link = checkNotNull(ExternalLinks(context).parse(url))
        assertEquals(null, link.intent.component)
        assertEquals(null, link.intent.selector)
        assertEquals("Only https fallbacks are honoured", null, link.fallbackUrl)
        assertTrue(link.intent.hasCategory(android.content.Intent.CATEGORY_BROWSABLE))
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}
