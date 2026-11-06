package com.seasaw.theta.feature.adblock.engine

/**
 * Maps a token found in a URL to the rules that require that token, so a request is checked against a handful of
 * candidates instead of the whole list. It is an open-addressing hash table over primitive arrays.
 *
 * Rules with no usable token go in [genericRules] and are checked on every request, so that list has to stay small.
 */
internal class TokenIndex(
    val slotKeys: IntArray,
    val slotStarts: IntArray,
    val postings: IntArray,
    val genericRules: IntArray,
) {
    private val mask = slotKeys.size - 1

    /** Returns true as soon as [predicate] accepts a candidate rule for [lowerUrl]. Allocates nothing per token. */
    inline fun anyCandidate(lowerUrl: String, predicate: (Int) -> Boolean): Boolean {
        for (rule in genericRules) {
            if (predicate(rule)) return true
        }
        if (slotKeys.isEmpty()) return false
        val length = lowerUrl.length
        var i = 0
        while (i < length) {
            val tokenStart = i
            while (i < length && isTokenChar(lowerUrl[i])) i++
            val slot = if (i - tokenStart >= MIN_TOKEN_LENGTH) findSlot(Hashing.fnv32(lowerUrl, tokenStart, i)) else -1
            if (slot >= 0 && anyInSlot(slot, predicate)) return true
            i++ // Step over the character that ended this run.
        }
        return false
    }

    inline fun anyInSlot(slot: Int, predicate: (Int) -> Boolean): Boolean {
        for (p in slotStarts[slot] until slotStarts[slot + 1]) {
            if (predicate(postings[p])) return true
        }
        return false
    }

    /** Slot ids are ordered so that a slot's postings end where the next slot's begin. */
    fun findSlot(hash: Int): Int {
        var slot = hash and mask
        while (true) {
            val key = slotKeys[slot]
            if (key == 0) return -1
            if (key == hash) return slot
            slot = (slot + 1) and mask
        }
    }

    companion object {
        const val MIN_TOKEN_LENGTH = 2

        fun isTokenChar(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9'

        fun build(ruleTokens: Map<Int, MutableList<Int>>, genericRules: List<Int>): TokenIndex {
            if (ruleTokens.isEmpty()) {
                return TokenIndex(IntArray(0), IntArray(1), IntArray(0), genericRules.toIntArray())
            }
            val capacity = Integer.highestOneBit(ruleTokens.size * 2 - 1) shl 1
            val keys = IntArray(capacity)
            val mask = capacity - 1
            val rulesBySlot = arrayOfNulls<List<Int>>(capacity)
            ruleTokens.forEach { (hash, rules) ->
                var slot = hash and mask
                while (keys[slot] != 0) slot = (slot + 1) and mask
                keys[slot] = hash
                rulesBySlot[slot] = rules
            }
            val starts = IntArray(capacity + 1)
            val postings = IntArray(ruleTokens.values.sumOf { it.size })
            var next = 0
            for (slot in 0 until capacity) {
                starts[slot] = next
                rulesBySlot[slot]?.forEach { postings[next++] = it }
            }
            starts[capacity] = next
            return TokenIndex(keys, starts, postings, genericRules.toIntArray())
        }
    }
}
