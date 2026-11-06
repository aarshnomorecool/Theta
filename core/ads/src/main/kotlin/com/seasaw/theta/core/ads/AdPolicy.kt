package com.seasaw.theta.core.ads

import com.seasaw.theta.core.common.url.UrlUtils

/**
 * Screens allowed to show ads, from ads rule 1. There is deliberately no entry for the browser screen, so "an ad next
 * to web content" cannot even be expressed. [analyticsId] is on the analytics allowlist.
 */
enum class AdPlacement(val analyticsId: String) {
    NEW_TAB("new_tab"),
    TAB_SWITCHER("tab_switcher"),
    DOWNLOADS("downloads"),
    BOOKMARKS("bookmarks"),
    HISTORY("history"),
}

/** Pure decisions from the ads rules, kept apart from the SDK so that they can be tested. */
object AdPolicy {
    /**
     * Ads rule 6: never show an ad on a screen where a YouTube page is visible. This matters on the tab switcher, whose
     * cards show page thumbnails.
     *
     * @param visibleHosts hosts of any page previews on the same screen
     */
    fun mayShowOnScreen(visibleHosts: Collection<String?>): Boolean = visibleHosts.none(UrlUtils::isYouTube)
}

/**
 * Ads rule 4: at most one interstitial per interval, none in a first session, none while a page loads.
 *
 * Time comes in as a parameter, and must be a monotonic clock in production: wall-clock time can be moved by the user,
 * which would break the cap in either direction.
 */
class InterstitialGate(private val minIntervalMillis: () -> Long) {
    private var lastShownAtMillis: Long? = null

    /** Whether a native-to-native transition may show an interstitial right now. */
    fun mayShow(
        nowMillis: Long,
        isFirstSession: Boolean,
        isPageLoading: Boolean,
        interstitialsEnabled: Boolean,
    ): Boolean {
        if (isFirstSession || isPageLoading || !interstitialsEnabled) return false
        val last = lastShownAtMillis ?: return true
        return nowMillis - last >= minIntervalMillis()
    }

    fun recordShown(nowMillis: Long) {
        lastShownAtMillis = nowMillis
    }
}
