package com.seasaw.theta.core.common.url

/** Dependency-free URL helpers. They run for every intercepted request, so they avoid java.net.URI parsing. */
object UrlUtils {
    // Not the full Public Suffix List, which is about 300 KB and costs too much on the size budget. These cover the
    // two-part suffixes our target markets use. A miss only makes two sites look first-party to each other.
    private val SECOND_LEVEL_SUFFIXES = setOf(
        "ac.in", "co.in", "edu.in", "firm.in", "gen.in", "gov.in", "ind.in", "mil.in", "net.in", "nic.in", "org.in",
        "res.in", "com.ru", "msk.ru", "net.ru", "org.ru", "spb.ru", "ac.uk", "co.uk", "gov.uk", "org.uk", "co.jp",
        "com.au", "net.au", "org.au", "com.br", "com.cn", "com.sg", "com.tr", "co.za",
    )

    private val YOUTUBE_DOMAINS = setOf("youtube.com", "youtu.be", "youtube-nocookie.com", "ytimg.com")
    private const val AUTHORITY_TERMINATORS = "/?#\\"
    private const val SCHEME_SEPARATOR_LENGTH = 3

    /** Returns the lowercase host of an http(s)-style URL, or null when there is none. */
    fun hostOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val start = schemeEnd + SCHEME_SEPARATOR_LENGTH
        var end = start
        // Backslash ends the authority too, matching browsers: https://evil.com\@bank.com goes to evil.com.
        while (end < url.length && url[end] !in AUTHORITY_TERMINATORS) end++
        val at = url.lastIndexOf('@', end - 1)
        val hostStart = if (at >= start) at + 1 else start
        if (hostStart >= end) return null
        val hostEnd = if (url[hostStart] == '[') {
            url.indexOf(']', hostStart).takeIf { it in hostStart until end }?.plus(1) ?: return null
        } else {
            url.lastIndexOf(':', end - 1).takeIf { it >= hostStart } ?: end
        }
        if (hostStart >= hostEnd) return null
        return url.substring(hostStart, hostEnd).lowercase().trimEnd('.').ifEmpty { null }
    }

    /** The domain a person would recognise as "the site": example.co.in for news.example.co.in. */
    fun registrableDomain(host: String): String {
        val lastDot = host.lastIndexOf('.')
        if (lastDot <= 0 || host.last().isDigit() || host.startsWith("[")) return host
        val secondLastDot = host.lastIndexOf('.', lastDot - 1)
        if (secondLastDot < 0) return host
        val lastTwo = host.substring(secondLastDot + 1)
        if (lastTwo !in SECOND_LEVEL_SUFFIXES) return lastTwo
        val thirdLastDot = host.lastIndexOf('.', secondLastDot - 1)
        return if (thirdLastDot < 0) host else host.substring(thirdLastDot + 1)
    }

    /** True when a request to [requestHost] leaves the site of [pageHost]. Unknown pages count as first party. */
    fun isThirdParty(requestHost: String, pageHost: String?): Boolean =
        pageHost != null && registrableDomain(requestHost) != registrableDomain(pageHost)

    /** Ads rule 6 needs this: no ad may share a screen with a YouTube page. */
    fun isYouTube(host: String?): Boolean = host != null && registrableDomain(host) in YOUTUBE_DOMAINS

    /**
     * The site a URL belongs to, used to group history: m.youtube.com and www.youtube.com are both youtube.com.
     * Null for addresses without a host, such as about:blank.
     */
    fun siteOf(url: String): String? = hostOf(url)?.let(::registrableDomain)

    /**
     * Short, readable form for lists: no scheme, no credentials, no www, no trailing slash. Credentials are dropped
     * because `https://bank.com@evil.com` is a phishing shape that would otherwise read as bank.com.
     */
    fun displayUrl(url: String): String {
        val withoutScheme = url.removePrefix("https://").removePrefix("http://")
        val authorityEnd = withoutScheme.indexOfFirst { it in AUTHORITY_TERMINATORS }
            .let { if (it < 0) withoutScheme.length else it }
        val at = if (withoutScheme.length == url.length) -1 else withoutScheme.lastIndexOf('@', authorityEnd - 1)
        return withoutScheme.substring(at + 1)
            .removePrefix("www.")
            .removeSuffix("/")
    }

    /**
     * Only the host, as people read it: no scheme, credentials, port, path or "www.". This is what the address bar
     * shows while not being edited, because a long path or a long subdomain can push the real site out of view.
     * Null for addresses without a host, such as about:blank.
     */
    fun displayHost(url: String): String? = hostOf(url)?.removePrefix("www.")?.ifEmpty { null }

    /** True for the addresses the browser UI may load directly: http(s) and about:blank. */
    fun isWebUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true) ||
        url.startsWith("http://", ignoreCase = true) ||
        url.equals("about:blank", ignoreCase = true)
}
