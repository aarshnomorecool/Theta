package com.seasaw.theta.feature.adblock.engine

/** Pattern rules as parallel arrays. See [DomainTrie] for why this avoids one object per rule. */
internal class RuleTable(
    val flags: IntArray,
    val patternStarts: IntArray,
    val patternBytes: ByteArray,
    val domainStarts: IntArray,
    val domainHashes: LongArray,
) {
    val size: Int get() = flags.size

    fun isImportant(rule: Int): Boolean = flags[rule] and FLAG_IMPORTANT != 0

    /** Cheap checks first: most candidates are rejected here without looking at the pattern. */
    fun matches(rule: Int, query: RuleQuery): Boolean {
        val ruleFlags = flags[rule]
        if (ruleFlags and query.typeMask == 0) return false
        if (ruleFlags and FLAG_THIRD_PARTY_ONLY != 0 && !query.isThirdParty) return false
        if (ruleFlags and FLAG_FIRST_PARTY_ONLY != 0 && query.isThirdParty) return false
        return patternMatches(rule, ruleFlags, query) && domainsAllow(rule, query)
    }

    private fun patternMatches(rule: Int, ruleFlags: Int, query: RuleQuery): Boolean {
        val text = if (ruleFlags and FLAG_MATCH_CASE != 0) query.url else query.lowerUrl
        val start = patternStarts[rule]
        val end = patternStarts[rule + 1]
        val endAnchored = ruleFlags and FLAG_END_ANCHOR != 0
        return when {
            ruleFlags and FLAG_HOST_ANCHOR != 0 -> matchesAtLabelStart(start, end, text, query, endAnchored)
            ruleFlags and FLAG_START_ANCHOR != 0 -> matchesFrom(start, end, text, 0, endAnchored)
            else -> matchesAnywhere(start, end, text, endAnchored)
        }
    }

    /** `||` means the pattern starts at the host or at any of its label boundaries. */
    private fun matchesAtLabelStart(
        start: Int,
        end: Int,
        text: String,
        query: RuleQuery,
        endAnchored: Boolean,
    ): Boolean {
        var labelStart = query.hostStart
        while (labelStart < query.hostEnd) {
            if (matchesFrom(start, end, text, labelStart, endAnchored)) return true
            val dot = text.indexOf('.', labelStart)
            if (dot < 0 || dot >= query.hostEnd) return false
            labelStart = dot + 1
        }
        return false
    }

    private fun matchesAnywhere(start: Int, end: Int, text: String, endAnchored: Boolean): Boolean {
        val first = patternBytes[start]
        if (first == STAR || first == CARET) return matchesFromEveryOffset(start, end, text, endAnchored)
        val firstChar = first.toInt().toChar()
        var from = text.indexOf(firstChar)
        while (from >= 0) {
            if (matchesFrom(start, end, text, from, endAnchored)) return true
            from = text.indexOf(firstChar, from + 1)
        }
        return false
    }

    private fun matchesFromEveryOffset(start: Int, end: Int, text: String, endAnchored: Boolean): Boolean {
        for (offset in 0..text.length) {
            if (matchesFrom(start, end, text, offset, endAnchored)) return true
        }
        return false
    }

    /** Glob match. `*` backtracks to the most recent star only, which is sufficient and avoids recursion. */
    private fun matchesFrom(start: Int, end: Int, text: String, textStart: Int, endAnchored: Boolean): Boolean {
        var p = start
        var t = textStart
        var starPattern = -1
        var starText = -1
        while (true) {
            if (p == end && (!endAnchored || t == text.length)) return true
            val next = if (p == end) MISMATCH else step(patternBytes[p], text, t)
            when (next) {
                MISMATCH -> {
                    if (starPattern < 0 || starText >= text.length) return false
                    starText++
                    t = starText
                    p = starPattern
                }

                WILDCARD -> {
                    p++
                    starPattern = p
                    starText = t
                }

                else -> {
                    p++
                    t = next
                }
            }
        }
    }

    /** Returns the text position after consuming [token] at [t], or [MISMATCH] or [WILDCARD]. */
    private fun step(token: Byte, text: String, t: Int): Int = when {
        token == STAR -> WILDCARD

        // The end of the URL also counts as a separator, and consumes no text.
        token == CARET -> when {
            t == text.length -> t
            isSeparator(text[t]) -> t + 1
            else -> MISMATCH
        }

        t < text.length && text[t].code == token.toInt() -> t + 1

        else -> MISMATCH
    }

    /** Implements `domain=`. The longest matching entry decides, so `a.com|~sub.a.com` excludes sub.a.com. */
    private fun domainsAllow(rule: Int, query: RuleQuery): Boolean {
        val start = domainStarts[rule]
        val end = domainStarts[rule + 1]
        if (start == end) return true
        val suffixes = query.pageHostSuffixHashes()
        for (suffix in suffixes) {
            for (i in start until end) {
                val entry = domainHashes[i]
                if (entry and DOMAIN_HASH_MASK ==
                    suffix and DOMAIN_HASH_MASK
                ) {
                    return entry and DOMAIN_EXCLUDED_BIT == 0L
                }
            }
        }
        for (i in start until end) {
            if (domainHashes[i] and DOMAIN_EXCLUDED_BIT == 0L) return false
        }
        return true
    }

    companion object {
        const val FLAG_TYPE_MASK = (1 shl 12) - 1
        const val FLAG_THIRD_PARTY_ONLY = 1 shl 12
        const val FLAG_FIRST_PARTY_ONLY = 1 shl 13
        const val FLAG_MATCH_CASE = 1 shl 14
        const val FLAG_IMPORTANT = 1 shl 15
        const val FLAG_HOST_ANCHOR = 1 shl 16
        const val FLAG_START_ANCHOR = 1 shl 17
        const val FLAG_END_ANCHOR = 1 shl 18

        /** The low bit of a stored domain hash carries whether the entry is an exclusion. */
        const val DOMAIN_EXCLUDED_BIT = 1L
        const val DOMAIN_HASH_MASK = -2L

        private const val MISMATCH = -1
        private const val WILDCARD = -2

        const val STAR: Byte = 42 // '*'
        const val CARET: Byte = 94 // '^'

        /** ABP defines `^` as anything but a letter, digit or one of _ - . % */
        private fun isSeparator(c: Char): Boolean = !(
            c in 'a'..'z' || c in '0'..'9' || c in 'A'..'Z' || c == '_' || c == '-' || c == '.' || c == '%'
            )
    }
}

/**
 * One request being matched. Built per request, so the page host suffixes are computed at most once.
 * Flat fields rather than a nested type: this exists on the hottest path in the browser.
 */
@Suppress("LongParameterList")
internal class RuleQuery(
    val url: String,
    val lowerUrl: String,
    val hostStart: Int,
    val hostEnd: Int,
    val typeMask: Int,
    val isThirdParty: Boolean,
    private val pageHost: String?,
) {
    private var suffixes: LongArray? = null

    /** Hashes of the page host and each of its parent domains, longest first. */
    fun pageHostSuffixHashes(): LongArray = suffixes ?: computeSuffixes().also { suffixes = it }

    private fun computeSuffixes(): LongArray {
        val host = pageHost ?: return LongArray(0)
        val hashes = LongArray(host.count { it == '.' } + 1)
        var start = 0
        var index = 0
        while (index < hashes.size) {
            hashes[index++] = Hashing.fnv64(host, start, host.length)
            start = host.indexOf('.', start) + 1
        }
        return hashes
    }
}
