package com.seasaw.theta.feature.adblock.engine

/** Turns filter list text into a [FilterSnapshot]. Runs at build time and in the weekly worker, never per request. */
class FilterCompiler {
    private val seenLines = HashSet<String>()
    private val rules = ArrayList<ParsedRule>()
    private val domains = DomainTrie.Builder()
    private val thirdPartyDomains = DomainTrie.Builder()

    fun addLine(line: String) {
        if (!seenLines.add(line)) return
        val rule = FilterParser.parse(line) ?: return
        val domain = plainDomainOf(rule)
        when {
            domain != null && rule.party == Party.ANY -> domains.add(domain)
            domain != null && rule.party == Party.THIRD -> thirdPartyDomains.add(domain)
            else -> rules += rule
        }
    }

    fun addLines(lines: Sequence<String>) = lines.forEach(::addLine)

    fun build(): FilterSnapshot {
        val ordered = rules.sortedWith(compareBy({ it.isException }, { it.pattern }))
        val table = buildRuleTable(ordered)
        val block = ordered.indices.filter { !ordered[it].isException }
        val exception = ordered.indices.filter { ordered[it].isException }
        val frequencies = tokenFrequencies(ordered)
        return FilterSnapshot(
            domains = domains.build(),
            thirdPartyDomains = thirdPartyDomains.build(),
            rules = table,
            blockIndex = buildIndex(ordered, block, frequencies),
            exceptionIndex = buildIndex(ordered, exception, frequencies),
        )
    }

    /** A bare `||example.com^` can be answered by the trie, which is faster and far smaller than a pattern rule. */
    private fun plainDomainOf(rule: ParsedRule): String? {
        val isSimpleBlock = !rule.isException &&
            rule.hostAnchored &&
            !rule.endAnchored &&
            !rule.important &&
            !rule.matchCase &&
            rule.typeMask == ResourceType.ALL_NETWORK &&
            rule.party != Party.FIRST &&
            rule.includeDomains.isEmpty() &&
            rule.excludeDomains.isEmpty()
        if (!isSimpleBlock || !rule.pattern.endsWith('^')) return null
        val domain = rule.pattern.dropLast(1)
        return domain.takeIf { value ->
            value.contains('.') &&
                value.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        }
    }

    private fun buildRuleTable(ordered: List<ParsedRule>): RuleTable {
        val flags = IntArray(ordered.size)
        val patternStarts = IntArray(ordered.size + 1)
        val domainStarts = IntArray(ordered.size + 1)
        val bytes = java.io.ByteArrayOutputStream()
        val hashes = ArrayList<Long>()

        ordered.forEachIndexed { index, rule ->
            flags[index] = flagsOf(rule)
            patternStarts[index] = bytes.size()
            rule.pattern.forEach { bytes.write(it.code) }
            domainStarts[index] = hashes.size
            rule.includeDomains.forEach { hashes += Hashing.fnv64(it) and RuleTable.DOMAIN_HASH_MASK }
            rule.excludeDomains.forEach {
                hashes += (Hashing.fnv64(it) and RuleTable.DOMAIN_HASH_MASK) or RuleTable.DOMAIN_EXCLUDED_BIT
            }
        }
        patternStarts[ordered.size] = bytes.size()
        domainStarts[ordered.size] = hashes.size
        return RuleTable(flags, patternStarts, bytes.toByteArray(), domainStarts, hashes.toLongArray())
    }

    private fun flagsOf(rule: ParsedRule): Int {
        var flags = rule.typeMask
        if (rule.party == Party.THIRD) flags = flags or RuleTable.FLAG_THIRD_PARTY_ONLY
        if (rule.party == Party.FIRST) flags = flags or RuleTable.FLAG_FIRST_PARTY_ONLY
        if (rule.matchCase) flags = flags or RuleTable.FLAG_MATCH_CASE
        if (rule.important) flags = flags or RuleTable.FLAG_IMPORTANT
        if (rule.hostAnchored) flags = flags or RuleTable.FLAG_HOST_ANCHOR
        if (rule.startAnchored) flags = flags or RuleTable.FLAG_START_ANCHOR
        if (rule.endAnchored) flags = flags or RuleTable.FLAG_END_ANCHOR
        return flags
    }

    private fun tokenFrequencies(ordered: List<ParsedRule>): Map<Int, Int> {
        val counts = HashMap<Int, Int>()
        ordered.forEach { rule -> eligibleTokens(rule).forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts
    }

    /** Each rule is filed under its rarest token, which keeps every bucket short. */
    private fun buildIndex(ordered: List<ParsedRule>, ruleIds: List<Int>, frequencies: Map<Int, Int>): TokenIndex {
        val byToken = HashMap<Int, MutableList<Int>>()
        val generic = ArrayList<Int>()
        ruleIds.forEach { id ->
            val best = eligibleTokens(ordered[id]).minByOrNull { frequencies[it] ?: 0 }
            if (best == null) generic += id else byToken.getOrPut(best) { ArrayList(2) } += id
        }
        return TokenIndex.build(byToken, generic)
    }

    /**
     * Tokens the URL is guaranteed to contain as a whole if this rule matches. A run touching `*`, or an unanchored
     * edge of the pattern, could be part of a longer run in the URL, so it would never be found by exact token lookup.
     */
    private fun eligibleTokens(rule: ParsedRule): Set<Int> {
        val pattern = rule.pattern.lowercase()
        val tokens = HashSet<Int>()
        var i = 0
        while (i < pattern.length) {
            if (!TokenIndex.isTokenChar(pattern[i])) {
                i++
                continue
            }
            val start = i
            while (i < pattern.length && TokenIndex.isTokenChar(pattern[i])) i++
            val startIsBounded = if (start == 0) rule.hostAnchored || rule.startAnchored else pattern[start - 1] != '*'
            val endIsBounded = if (i == pattern.length) rule.endAnchored else pattern[i] != '*'
            val isUseful = i - start >= TokenIndex.MIN_TOKEN_LENGTH && pattern.substring(start, i) !in COMMON_URL_TOKENS
            if (startIsBounded && endIsBounded && isUseful) tokens += Hashing.fnv32(pattern, start, i)
        }
        return tokens
    }

    private companion object {
        val COMMON_URL_TOKENS = setOf("http", "https", "www", "com", "net", "org", "html", "js", "php", "in")
    }
}
