package com.seasaw.theta.core.common.url

import java.net.IDN

/** What the user meant by the text they submitted in the address bar. */
sealed interface AddressInput {
    data class Url(val url: String) : AddressInput
    data class Search(val query: String) : AddressInput
}

/**
 * Decides whether address bar text is a URL or a search.
 *
 * Only http(s) and about:blank are navigable from here. Everything else with a scheme becomes a search,
 * because treating `javascript:` or `file:` as navigation would let pasted text run script in the current
 * page or reach local files.
 */
object AddressBarParser {
    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val IPV6_LITERAL = Regex("^\\[[0-9a-fA-F:.]+]$")
    private val PORT = Regex("^\\d{1,5}$")
    private const val AUTHORITY_TERMINATORS = "/?#\\"
    private const val MAX_PORT = 65535
    private const val MAX_IPV4_OCTET = 255

    // Valid TLDs too, but people searching for "node.js" far outnumber people visiting a bare ".js" host.
    // A path, port or explicit scheme still makes these navigate.
    private val LIKELY_FILE_SUFFIXES = setOf(
        "apk", "bat", "c", "cpp", "css", "csv", "doc", "docx", "exe", "gif", "h", "htm", "html", "java", "jpeg", "jpg",
        "js", "json", "kt", "md", "mkv", "mov", "mp3", "mp4", "pdf", "png", "ppt", "pptx", "py", "rar", "rs", "sh",
        "sql", "svg", "ts", "txt", "wav", "webp", "xls", "xlsx", "xml", "zip",
    )

    fun parse(rawInput: String): AddressInput? {
        val input = rawInput.trim()
        if (input.isEmpty()) return null
        if (input.any { it.isWhitespace() }) return AddressInput.Search(input)
        if (input.equals("about:blank", ignoreCase = true)) return AddressInput.Url("about:blank")

        val scheme = SCHEME.find(input)
        if (scheme != null && !looksLikeHostAndPort(input)) {
            val name = scheme.groupValues[1].lowercase()
            val rest = scheme.groupValues[2]
            val isHttp = (name == "http" || name == "https") && rest.startsWith("//") && rest.length > 2
            return if (isHttp) toUrl(input, schemePresent = true) else AddressInput.Search(input)
        }
        return toUrl(input, schemePresent = false)
    }

    /** Resolves [rawInput] straight to a loadable URL, using [engine] when it is a search. */
    fun resolve(rawInput: String, engine: SearchEngine): String? = when (val parsed = parse(rawInput)) {
        is AddressInput.Url -> parsed.url
        is AddressInput.Search -> engine.searchUrl(parsed.query)
        null -> null
    }

    private fun looksLikeHostAndPort(input: String): Boolean {
        val afterColon = input.substringAfter(':').substringBefore('/')
        return PORT.matches(afterColon) && !input.substringAfter(':').startsWith("//")
    }

    private fun toUrl(input: String, schemePresent: Boolean): AddressInput {
        val withoutScheme = if (schemePresent) input.substringAfter("://") else input
        val rawAuthority = withoutScheme.takeWhile { it !in AUTHORITY_TERMINATORS }
        val tail = withoutScheme.substring(rawAuthority.length)
        val authority = parseAuthority(rawAuthority)
        val isExplicit = schemePresent || tail.isNotEmpty() || authority.port.isNotEmpty()

        val asciiHost = toAsciiHost(authority.host).takeIf { isNavigable(authority, isExplicit) }
            ?: return AddressInput.Search(input)
        val scheme = when {
            schemePresent -> input.substringBefore("://").lowercase()
            isLocalAddress(authority.host) -> "http"
            else -> "https"
        }
        val portPart = if (authority.port.isEmpty()) "" else ":${authority.port}"
        return AddressInput.Url("$scheme://$asciiHost$portPart$tail")
    }

    private data class Authority(val host: String, val port: String)

    /** Credentials in a typed URL are a classic phishing shape (https://bank.com@evil.com), so they are dropped. */
    private fun parseAuthority(raw: String): Authority {
        val authority = raw.substringAfterLast('@')
        if (!authority.startsWith(
                "[",
            )
        ) {
            return Authority(authority.substringBefore(':'), authority.substringAfter(':', ""))
        }
        return Authority(authority.substringBefore("]") + "]", authority.substringAfter("]", "").removePrefix(":"))
    }

    private fun isNavigable(authority: Authority, isExplicit: Boolean): Boolean {
        val portIsValid = authority.port.isEmpty() || isValidPort(authority.port)
        return isValidHost(authority.host) && portIsValid && (isExplicit || !hasFileLikeSuffix(authority.host))
    }

    private fun isValidPort(port: String): Boolean = PORT.matches(port) && port.toInt() in 1..MAX_PORT

    private fun isValidHost(host: String): Boolean = when {
        host.isEmpty() -> false
        IPV6_LITERAL.matches(host) -> true
        IPV4.matches(host) -> host.split('.').all { it.toInt() in 0..MAX_IPV4_OCTET }
        host.equals("localhost", ignoreCase = true) -> true
        else -> hasValidLabels(host)
    }

    private fun hasValidLabels(host: String): Boolean {
        val labels = host.trimEnd('.').split('.')
        if (labels.size < 2) return false
        val labelsAreWellFormed = labels.all { label ->
            label.isNotEmpty() &&
                label.first().isLetterOrDigit() &&
                label.last() != '-' &&
                label.all { it == '-' || isDomainCharacter(it) }
        }
        val tld = labels.last()
        val tldIsWellFormed = tld.all { it.isLetter() || isCombiningMark(it) } || tld.startsWith("xn--", true)
        return labelsAreWellFormed && tld.length >= 2 && tldIsWellFormed
    }

    private fun isDomainCharacter(c: Char): Boolean = c.isLetterOrDigit() || isCombiningMark(c)

    // Devanagari vowel signs are combining marks rather than letters, so a letters-only check rejects Hindi domains.
    private fun isCombiningMark(c: Char): Boolean =
        c.category == CharCategory.COMBINING_SPACING_MARK || c.category == CharCategory.NON_SPACING_MARK

    private fun hasFileLikeSuffix(host: String): Boolean =
        host.substringAfterLast('.').lowercase() in LIKELY_FILE_SUFFIXES

    private fun isLocalAddress(host: String): Boolean = host.equals("localhost", ignoreCase = true) ||
        IPV6_LITERAL.matches(host) ||
        IPV4.matches(host)

    /** Converts Unicode domains such as भारत.भारत to the punycode form the network layer needs. */
    private fun toAsciiHost(host: String): String? = when {
        IPV6_LITERAL.matches(host) -> host
        else -> runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase() }.getOrNull()
    }
}
