package com.seasaw.theta.core.ads

import com.seasaw.theta.core.common.url.UrlUtils

/**
 * Screens allowed to show ads, from ads rule 1. There is deliberately no entry for the browser screen, so "an ad next
 * to web content" cannot even be expressed. [analyticsId] is on the analytics allowlist.
 */
enum class AdPlacement(val analyticsId: String) {
    NEW_TAB("new_tab"),
    DOWNLOADS("downloads"),
    BOOKMARKS("bookmarks"),
    HISTORY("history"),
}

/** Pure decisions from the ads rules, kept apart from the SDK so that they can be tested. */
object AdPolicy {
    /**
     * Ads rule 6: never show an ad on a screen where a YouTube page is visible.
     *
     * @param visibleHosts hosts of any page previews on the same screen
     */
    fun mayShowOnScreen(visibleHosts: Collection<String?>): Boolean = visibleHosts.none(UrlUtils::isYouTube)
}
