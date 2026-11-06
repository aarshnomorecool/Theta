package com.seasaw.theta.feature.adblock.engine

/** Everything the matcher consults. Immutable, so one instance can serve every WebView thread without locks. */
class FilterSnapshot internal constructor(
    internal val domains: DomainTrie,
    internal val thirdPartyDomains: DomainTrie,
    internal val rules: RuleTable,
    internal val blockIndex: TokenIndex,
    internal val exceptionIndex: TokenIndex,
) {
    /** Number of network rules this snapshot enforces. Used to reject a suspiciously small list update. */
    val ruleCount: Int get() = rules.size + domains.terminals.count { it } + thirdPartyDomains.terminals.count { it }

    internal val genericRuleCount: Int get() = blockIndex.genericRules.size + exceptionIndex.genericRules.size
}

/** Answers "should this request be blocked". Called from WebView IO threads, so it must stay thread safe. */
class FilterEngine(private val snapshot: FilterSnapshot) {
    val ruleCount: Int get() = snapshot.ruleCount

    /**
     * @param url the request URL
     * @param host lowercase host of [url]
     * @param pageUrl URL of the top-level page, when known
     * @param pageHost lowercase host of [pageUrl]
     * @param isThirdParty whether [host] is a different site from [pageHost]
     */
    @Suppress("LongParameterList") // Flat on purpose: this runs per request, so it should not allocate a wrapper.
    fun shouldBlock(
        url: String,
        host: String,
        pageUrl: String?,
        pageHost: String?,
        isThirdParty: Boolean,
        type: ResourceType,
    ): Boolean {
        // Blocking a page the user navigated to would look like the browser is broken. Only subresources are blocked.
        if (type == ResourceType.DOCUMENT) return false
        val query = newQuery(url, host, type.mask, isThirdParty, pageHost) ?: return false
        if (!isBlockedByList(query, host, isThirdParty)) return false
        val isExcepted = matchesException(query) || isPageExcepted(pageUrl, pageHost)
        return !isExcepted || snapshot.blockIndex.anyCandidate(query.lowerUrl) { important(it, query) }
    }

    private fun important(rule: Int, query: RuleQuery): Boolean =
        snapshot.rules.isImportant(rule) && snapshot.rules.matches(rule, query)

    private fun isBlockedByList(query: RuleQuery, host: String, isThirdParty: Boolean): Boolean {
        if (snapshot.domains.containsHostOrParent(host)) return true
        if (isThirdParty && snapshot.thirdPartyDomains.containsHostOrParent(host)) return true
        return snapshot.blockIndex.anyCandidate(query.lowerUrl) { snapshot.rules.matches(it, query) }
    }

    private fun matchesException(query: RuleQuery): Boolean =
        snapshot.exceptionIndex.anyCandidate(query.lowerUrl) { snapshot.rules.matches(it, query) }

    /** `@@||site^$document` turns blocking off for everything on that page. */
    private fun isPageExcepted(pageUrl: String?, pageHost: String?): Boolean {
        if (pageUrl == null || pageHost == null) return false
        val pageQuery = newQuery(pageUrl, pageHost, ResourceType.DOCUMENT.mask, false, pageHost) ?: return false
        return matchesException(pageQuery)
    }

    private fun newQuery(
        url: String,
        host: String,
        typeMask: Int,
        isThirdParty: Boolean,
        pageHost: String?,
    ): RuleQuery? {
        // Tracking URLs can reach many KB. Ad-identifying text sits near the start, so the tail costs time for nothing.
        val bounded = if (url.length > MAX_URL_LENGTH) url.substring(0, MAX_URL_LENGTH) else url
        val lower = bounded.lowercase()
        val schemeEnd = lower.indexOf("://")
        if (schemeEnd < 0) return null
        val hostStart = lower.indexOf(host, schemeEnd)
        if (hostStart < 0) return null
        return RuleQuery(bounded, lower, hostStart, hostStart + host.length, typeMask, isThirdParty, pageHost)
    }

    private companion object {
        const val MAX_URL_LENGTH = 2048
    }
}
