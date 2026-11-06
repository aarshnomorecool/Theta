package com.seasaw.theta.feature.adblock.engine

/** Resource categories from the Adblock Plus filter syntax. Bit values are part of the snapshot format. */
enum class ResourceType(val mask: Int) {
    SCRIPT(1 shl 0),
    IMAGE(1 shl 1),
    STYLESHEET(1 shl 2),
    OBJECT(1 shl 3),
    XHR(1 shl 4),
    SUBDOCUMENT(1 shl 5),
    PING(1 shl 6),
    WEBSOCKET(1 shl 7),
    MEDIA(1 shl 8),
    FONT(1 shl 9),
    OTHER(1 shl 10),
    DOCUMENT(1 shl 11),
    ;

    companion object {
        /** Every network type. DOCUMENT is excluded: it means "the page itself", and only exceptions may name it. */
        const val ALL_NETWORK = (1 shl 11) - 1
    }
}

internal enum class Party { ANY, FIRST, THIRD }

internal data class ParsedRule(
    val pattern: String,
    val isException: Boolean,
    val hostAnchored: Boolean,
    val startAnchored: Boolean,
    val endAnchored: Boolean,
    val typeMask: Int,
    val party: Party,
    val matchCase: Boolean,
    val important: Boolean,
    val includeDomains: List<String>,
    val excludeDomains: List<String>,
)

/**
 * Parses the network subset of the Adblock Plus syntax.
 *
 * A rule using anything not fully understood is dropped rather than approximated. Ignoring an unknown option could
 * widen the rule, and over-blocking breaks sites, which is much worse for users than missing one ad.
 */
internal object FilterParser {
    private const val MIN_PATTERN_LENGTH = 3
    private const val MAX_ASCII = 127
    private val NEGATABLE_FLAGS = setOf("third-party", "3p", "first-party", "1p", "match-case")

    private val TYPE_OPTIONS = mapOf(
        "script" to ResourceType.SCRIPT.mask,
        "image" to ResourceType.IMAGE.mask,
        "img" to ResourceType.IMAGE.mask,
        "stylesheet" to ResourceType.STYLESHEET.mask,
        "css" to ResourceType.STYLESHEET.mask,
        "object" to ResourceType.OBJECT.mask,
        "object-subrequest" to ResourceType.OBJECT.mask,
        "xmlhttprequest" to ResourceType.XHR.mask,
        "xhr" to ResourceType.XHR.mask,
        "subdocument" to ResourceType.SUBDOCUMENT.mask,
        "frame" to ResourceType.SUBDOCUMENT.mask,
        "ping" to ResourceType.PING.mask,
        "beacon" to ResourceType.PING.mask,
        "websocket" to ResourceType.WEBSOCKET.mask,
        "media" to ResourceType.MEDIA.mask,
        "font" to ResourceType.FONT.mask,
        "other" to ResourceType.OTHER.mask,
    )

    fun parse(line: String): ParsedRule? {
        val text = line.trim()
        if (isCommentOrCosmetic(text)) return null

        val isException = text.startsWith("@@")
        val body = if (isException) text.substring(2) else text
        val optionsAt = optionsStart(body)
        val rawPattern = if (optionsAt >= 0) body.substring(0, optionsAt) else body
        val isRegex = rawPattern.length >= 2 && rawPattern.startsWith('/') && rawPattern.endsWith('/')
        // Regex rules are skipped on purpose: they cannot be indexed, and they put the 1 ms budget at risk.
        if (isRegex || rawPattern.any { it.code > MAX_ASCII }) return null

        val options = if (optionsAt >= 0) parseOptions(body.substring(optionsAt + 1), isException) else DEFAULT_OPTIONS
        if (options == null) return null
        return buildRule(rawPattern, isException, options)
    }

    private fun buildRule(rawPattern: String, isException: Boolean, options: RuleOptions): ParsedRule? {
        var pattern = rawPattern
        val hostAnchored = pattern.startsWith("||")
        val startAnchored = !hostAnchored && pattern.startsWith("|")
        if (hostAnchored) pattern = pattern.substring(2)
        if (startAnchored) pattern = pattern.substring(1)
        val endAnchored = pattern.endsWith("|")
        if (endAnchored) pattern = pattern.dropLast(1)
        pattern = pattern.replace(Regex("\\*{2,}"), "*")

        // A wildcard at the edge makes the anchor meaningless and adds nothing to an unanchored rule.
        if (!hostAnchored && !startAnchored) pattern = pattern.trimStart('*')
        if (!endAnchored) pattern = pattern.trimEnd('*')

        val literalCount = pattern.count { it != '*' && it != '^' }
        if (literalCount < MIN_PATTERN_LENGTH) return null
        return ParsedRule(
            pattern = if (options.matchCase) pattern else pattern.lowercase(),
            isException = isException,
            hostAnchored = hostAnchored,
            startAnchored = startAnchored,
            endAnchored = endAnchored,
            typeMask = options.typeMask,
            party = options.party,
            matchCase = options.matchCase,
            important = options.important,
            includeDomains = options.includeDomains,
            excludeDomains = options.excludeDomains,
        )
    }

    private fun isCommentOrCosmetic(text: String): Boolean =
        text.isEmpty() || text[0] == '!' || text[0] == '[' || isCosmetic(text)

    private fun isCosmetic(text: String): Boolean {
        val hash = text.indexOf('#')
        if (hash < 0 || hash + 1 >= text.length) return false
        val next = text[hash + 1]
        val isTwoCharMarker = next == '#'
        val isThreeCharMarker = (next == '@' || next == '?' || next == '$' || next == '%') &&
            hash + 2 < text.length &&
            (text[hash + 2] == '#' || text[hash + 2] == '?' || text[hash + 2] == '$')
        return isTwoCharMarker || isThreeCharMarker
    }

    /** The options separator is the last `$` followed by something shaped like options. `/a$b/` has none. */
    private fun optionsStart(body: String): Int {
        val dollar = body.lastIndexOf('$')
        if (dollar < 0 || dollar == body.length - 1) return -1
        val tail = body.substring(dollar + 1)
        return if (tail.first().isLetter() || tail.first() == '~') dollar else -1
    }

    private data class RuleOptions(
        val typeMask: Int,
        val party: Party,
        val matchCase: Boolean,
        val important: Boolean,
        val includeDomains: List<String>,
        val excludeDomains: List<String>,
    )

    private val DEFAULT_OPTIONS = RuleOptions(
        typeMask = ResourceType.ALL_NETWORK,
        party = Party.ANY,
        matchCase = false,
        important = false,
        includeDomains = emptyList(),
        excludeDomains = emptyList(),
    )

    private class OptionState {
        var included = 0
        var excluded = 0
        var sawPopupOnly = false
        var party = Party.ANY
        var matchCase = false
        var important = false
        var document = false
        val includeDomains = mutableListOf<String>()
        val excludeDomains = mutableListOf<String>()
    }

    private fun parseOptions(raw: String, isException: Boolean): RuleOptions? {
        val state = OptionState()
        val understood = raw.split(',').all { parseOption(it.trim(), state) }
        if (!understood) return null
        // A block rule naming only popups does not apply here: popups never reach shouldInterceptRequest.
        if (state.sawPopupOnly && state.included == 0 && !state.document) return null
        if (state.document && !isException) return null

        var mask = if (state.included != 0) state.included else ResourceType.ALL_NETWORK
        mask = mask and state.excluded.inv()
        if (state.document) {
            mask =
                if (state.included != 0) mask or ResourceType.DOCUMENT.mask else ResourceType.DOCUMENT.mask
        }
        if (mask == 0) return null
        return RuleOptions(
            mask,
            state.party,
            state.matchCase,
            state.important,
            state.includeDomains,
            state.excludeDomains,
        )
    }

    private fun parseOption(option: String, state: OptionState): Boolean {
        val negated = option.startsWith('~')
        val name = option.removePrefix("~").substringBefore('=').lowercase()
        val typeMask = TYPE_OPTIONS[name]
        return when {
            typeMask == null && (name == "domain" || name == "from") ->
                parseDomains(option.substringAfter('=', ""), state)

            typeMask == null -> parseFlag(name, negated, state)

            else -> {
                if (negated) {
                    state.excluded = state.excluded or typeMask
                } else {
                    state.included =
                        state.included or typeMask
                }
                true
            }
        }
    }

    /** Returns false for any option not understood, which makes the caller drop the rule. */
    private fun parseFlag(name: String, negated: Boolean, state: OptionState): Boolean {
        when (name) {
            "third-party", "3p" -> state.party = if (negated) Party.FIRST else Party.THIRD
            "first-party", "1p" -> state.party = if (negated) Party.THIRD else Party.FIRST
            "match-case" -> state.matchCase = !negated
            "important" -> state.important = true
            "document", "doc" -> state.document = !negated
            "popup", "popunder" -> state.sawPopupOnly = true
            "all" -> state.included = state.included or ResourceType.ALL_NETWORK
            else -> return false
        }
        return !negated || name in NEGATABLE_FLAGS
    }

    private fun parseDomains(value: String, state: OptionState): Boolean {
        if (value.isEmpty()) return false
        value.split('|').forEach { entry ->
            val domain = entry.removePrefix("~").lowercase()
            // Regex and entity (example.*) domains cannot be hashed for lookup, so such a rule is not understood.
            if (domain.isEmpty() || domain.contains('*') || domain.contains('/')) return false
            if (entry.startsWith('~')) state.excludeDomains += domain else state.includeDomains += domain
        }
        return true
    }
}
