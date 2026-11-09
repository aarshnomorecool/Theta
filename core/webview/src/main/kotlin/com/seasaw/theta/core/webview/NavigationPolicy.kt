package com.seasaw.theta.core.webview

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface NavigationDecision {
    data object LoadInWebView : NavigationDecision

    /** Dropped silently. Pages have no legitimate reason to navigate the top frame to these. */
    data object Block : NavigationDecision

    /** Belongs to another app, which is opened only after the user agrees. */
    data object AskToOpenExternally : NavigationDecision
}

object NavigationPolicy {
    private val WEB_SCHEMES = setOf("http", "https", "about", "blob")

    // Local data or script execution. A page redirecting here must never get as far as a WebView or an intent.
    private val BLOCKED_SCHEMES = setOf("file", "content", "javascript", "data", "chrome", "view-source", "ws", "wss")

    fun decide(url: String): NavigationDecision {
        val scheme = url.substringBefore(':', "").lowercase()
        return when (scheme) {
            "" -> NavigationDecision.Block
            in WEB_SCHEMES -> NavigationDecision.LoadInWebView
            in BLOCKED_SCHEMES -> NavigationDecision.Block
            else -> NavigationDecision.AskToOpenExternally
        }
    }
}

/** An app-link a page wants to open. The UI must ask the user before calling [ExternalLinks.launch]. */
data class ExternalLink(val intent: Intent, val fallbackUrl: String?, val scheme: String)

sealed interface ExternalLaunchResult {
    data object Launched : ExternalLaunchResult
    data class NoAppInstalled(val fallbackUrl: String?) : ExternalLaunchResult
}

@Singleton
class ExternalLinks @Inject constructor(@ApplicationContext private val context: Context) {
    /** Returns null when [url] cannot be turned into a safe intent. */
    fun parse(url: String): ExternalLink? {
        val scheme = url.substringBefore(':', "").lowercase()
        val parsed = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (ignored: URISyntaxException) {
            return null
        }
        // A page has no business pointing another app at local files, or routing an intent back into this app.
        if (parsed.data?.scheme?.lowercase() in LOCAL_DATA_SCHEMES) return null
        if (parsed.`package` == context.packageName) return null
        val fallback = parsed.getStringExtra(EXTRA_FALLBACK_URL)?.takeIf { it.startsWith("https://") }
        return ExternalLink(sanitize(parsed), fallback, scheme)
    }

    /** Only call after the user has agreed. Pages can never reach this on their own. */
    fun launch(link: ExternalLink, activityContext: Context): ExternalLaunchResult = try {
        activityContext.startActivity(link.intent)
        ExternalLaunchResult.Launched
    } catch (ignored: android.content.ActivityNotFoundException) {
        ExternalLaunchResult.NoAppInstalled(link.fallbackUrl)
    } catch (ignored: SecurityException) {
        ExternalLaunchResult.NoAppInstalled(link.fallbackUrl)
    }

    /**
     * An `intent://` URL is attacker-controlled and can name any component in any app. Stripping these fields limits it
     * to what a BROWSABLE link may do, and stops it from reaching our non-exported components or granting URI access.
     */
    private fun sanitize(intent: Intent): Intent = intent.apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        component = null
        selector = null
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    private companion object {
        const val EXTRA_FALLBACK_URL = "browser_fallback_url"
        val LOCAL_DATA_SCHEMES = setOf("file", "content")
    }
}
