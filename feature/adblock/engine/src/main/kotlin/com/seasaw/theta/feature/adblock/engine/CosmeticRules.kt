package com.seasaw.theta.feature.adblock.engine

/**
 * Element hiding ("cosmetic") rules from a filter list: CSS selectors for things to hide on a page. They never touch
 * the network, only what is drawn.
 *
 * Most of a list is generic `##.cookie-banner` style rules. Rather than send thousands of selectors to every page, the
 * page reports the ids and classes it uses, and only the generic rules naming one of those are returned. Site rules
 * (`example.com##.notice`) are few per site, so they are returned whole.
 */
class CosmeticRules internal constructor(
    /** Generic `###id` rules, by id. */
    internal val genericIds: Set<String>,
    /** Generic `##.class` rules, by class. */
    internal val genericClasses: Set<String>,
    /** Generic rules more involved than one id or class. Sent to every page, so the list is kept short by the lists. */
    internal val genericComplex: List<String>,
    /** Site rules, by domain without "www.". */
    internal val siteSelectors: Map<String, List<String>>,
    /** Selectors a site has switched off with `example.com#@#selector`. */
    internal val siteExceptions: Map<String, Set<String>>,
) {
    val ruleCount: Int
        get() = genericIds.size + genericClasses.size + genericComplex.size + siteSelectors.values.sumOf { it.size }

    /** The selectors to hide on [host], given the ids and classes the page is using right now. */
    fun selectorsFor(host: String, ids: Collection<String>, classes: Collection<String>): List<String> {
        val domains = domainsOf(host)
        val excepted = domains.flatMapTo(HashSet()) { siteExceptions[it].orEmpty() }
        val result = LinkedHashSet<String>()
        domains.forEach { domain -> siteSelectors[domain]?.let(result::addAll) }
        ids.forEach { id -> if (id in genericIds) result += "#$id" }
        classes.forEach { name -> if (name in genericClasses) result += ".$name" }
        result += genericComplex
        result.removeAll(excepted)
        return result.toList()
    }

    companion object {
        val Empty = CosmeticRules(emptySet(), emptySet(), emptyList(), emptyMap(), emptyMap())

        /** `a.b.example.com` gives itself and every parent, so a rule for `example.com` covers its subdomains. */
        internal fun domainsOf(host: String): List<String> {
            val clean = host.lowercase().removePrefix("www.").trimEnd('.')
            if (clean.isEmpty()) return emptyList()
            val parts = clean.split('.')
            return parts.indices.map { parts.subList(it, parts.size).joinToString(".") }
        }
    }
}

/**
 * Collects element hiding rules from list text. Lines it does not fully understand are dropped: a misread selector
 * could hide a page's real content, which is far worse than leaving a pop-up in place.
 */
class CosmeticCompiler {
    private val genericIds = HashSet<String>()
    private val genericClasses = HashSet<String>()
    private val genericComplex = LinkedHashSet<String>()
    private val genericExceptions = HashSet<String>()
    private val siteSelectors = HashMap<String, LinkedHashSet<String>>()
    private val siteExceptions = HashMap<String, HashSet<String>>()

    fun addLine(line: String) {
        val text = line.trim()
        if (text.isEmpty() || text[0] == '!' || text[0] == '[') return
        val exceptionAt = text.indexOf(EXCEPT)
        val hideAt = text.indexOf(HIDE)
        when {
            exceptionAt >= 0 ->
                addException(text.substring(0, exceptionAt), text.substring(exceptionAt + EXCEPT.length))

            hideAt >= 0 -> addHide(text.substring(0, hideAt), text.substring(hideAt + HIDE.length))
        }
    }

    fun addLines(lines: Sequence<String>) = lines.forEach(::addLine)

    private fun addHide(domainPart: String, selector: String) {
        if (!isSafeSelector(selector)) return
        val (included, excluded) = splitDomains(domainPart) ?: return
        if (included.isEmpty()) {
            addGeneric(selector)
            // "~site.com##x" means everywhere except site.com.
            excluded.forEach { siteExceptions.getOrPut(it, ::HashSet) += selector }
        } else {
            included.forEach { siteSelectors.getOrPut(it, ::LinkedHashSet) += selector }
        }
    }

    private fun addException(domainPart: String, selector: String) {
        if (!isSafeSelector(selector)) return
        val (included, _) = splitDomains(domainPart) ?: return
        if (included.isEmpty()) {
            genericExceptions += selector
        } else {
            included.forEach { siteExceptions.getOrPut(it, ::HashSet) += selector }
        }
    }

    private fun addGeneric(selector: String) {
        val simple = SIMPLE.matchEntire(selector)
        when {
            simple == null -> genericComplex += selector
            selector[0] == '#' -> genericIds += simple.groupValues[1]
            else -> genericClasses += simple.groupValues[1]
        }
    }

    fun build(): CosmeticRules {
        val ids = genericIds.filterTo(HashSet()) { "#$it" !in genericExceptions }
        val classes = genericClasses.filterTo(HashSet()) { ".$it" !in genericExceptions }
        val complex = genericComplex.filter { it !in genericExceptions }
        return CosmeticRules(
            genericIds = ids,
            genericClasses = classes,
            genericComplex = complex,
            siteSelectors = siteSelectors.mapValues { it.value.toList() },
            siteExceptions = siteExceptions,
        )
    }

    private companion object {
        const val HIDE = "##"
        const val EXCEPT = "#@#"

        /** One id or class. A leading digit is not a valid CSS identifier, so such rules count as complex and drop. */
        val SIMPLE = Regex("""[#.](-?[A-Za-z_][\w-]*)""")
        const val MAX_SELECTOR_LENGTH = 300

        /** Extended syntax that only some blockers run as scripts. Plain CSS cannot express it, so it is skipped. */
        val PROCEDURAL = listOf(
            ":-abp-", ":has-text(", ":xpath(", ":matches-css", ":style(", ":upward(", ":remove(", ":watch-attr(",
            ":min-text-length(", ":matches-path(", ":matches-attr(", ":others(", ":nth-ancestor(", ":if(", ":if-not(",
            "+js(",
        )

        /**
         * The selector is placed inside a stylesheet, so it must not be able to close the rule and add its own CSS.
         * Lists come over https from a trusted source, but this keeps a bad list from doing more than hiding things.
         */
        fun isSafeSelector(selector: String): Boolean = selector.isNotBlank() &&
            selector.length <= MAX_SELECTOR_LENGTH &&
            selector.none { it == '{' || it == '}' || it == '<' || it == '\n' || it == '\\' || it == '@' } &&
            !selector.contains("/*") &&
            PROCEDURAL.none { selector.contains(it) } &&
            !selector.startsWith('?') &&
            !selector.startsWith('$') &&
            !selector.startsWith('%')

        /** Null for anything but plain domains. Lists also use regex and entity forms, which this does not support. */
        fun splitDomains(part: String): Pair<List<String>, List<String>>? {
            if (part.isEmpty()) return emptyList<String>() to emptyList()
            val included = ArrayList<String>()
            val excluded = ArrayList<String>()
            part.split(',').forEach { raw ->
                val negated = raw.startsWith('~')
                val domain = raw.removePrefix("~").trim().lowercase().removePrefix("www.")
                if (!DOMAIN.matches(domain)) return null
                if (negated) excluded += domain else included += domain
            }
            return included to excluded
        }

        val DOMAIN = Regex("""[a-z0-9-]+(\.[a-z0-9-]+)+""")
    }
}
