package com.seasaw.theta.feature.adblock.engine

internal object Hashing {
    private const val FNV64_OFFSET = -3750763034362895579L
    private const val FNV64_PRIME = 1099511628211L
    private const val FNV32_OFFSET = -2128831035
    private const val FNV32_PRIME = 16777619

    fun fnv64(text: CharSequence, start: Int = 0, end: Int = text.length): Long {
        var hash = FNV64_OFFSET
        for (i in start until end) {
            hash = (hash xor text[i].code.toLong()) * FNV64_PRIME
        }
        return hash
    }

    /** Never returns 0, which [TokenIndex] reserves to mean an empty slot. */
    fun fnv32(text: CharSequence, start: Int, end: Int): Int {
        var hash = FNV32_OFFSET
        for (i in start until end) {
            hash = (hash xor text[i].code) * FNV32_PRIME
        }
        return if (hash == 0) 1 else hash
    }
}

/**
 * A trie of domains keyed on reversed labels (com -> example -> ads), held in flat arrays.
 *
 * Tens of thousands of domains as objects would cost several MB of heap on a phone whose idle budget is tight. Here a
 * node is just an index. Nodes are numbered breadth first, so a node's children sit next to each other, sorted by label
 * hash, and a child lookup is a binary search. A lookup therefore costs O(labels x log children) with no allocation.
 */
internal class DomainTrie(val labelHashes: LongArray, val childStarts: IntArray, val terminals: BooleanArray) {
    val size: Int get() = labelHashes.size

    /** True when [host] or any parent domain of it is in the trie. [host] must be lowercase. */
    fun containsHostOrParent(host: String, start: Int = 0, end: Int = host.length): Boolean {
        if (size == 0) return false
        var node = ROOT
        var labelEnd = end
        while (labelEnd > start) {
            var labelStart = labelEnd - 1
            while (labelStart > start && host[labelStart - 1] != '.') labelStart--
            node = findChild(node, Hashing.fnv64(host, labelStart, labelEnd))
            if (node < 0) return false
            if (terminals[node]) return true
            labelEnd = labelStart - 1
        }
        return false
    }

    private fun findChild(node: Int, labelHash: Long): Int {
        var low = childStarts[node]
        var high = childStarts[node + 1] - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val value = labelHashes[middle]
            when {
                value < labelHash -> low = middle + 1
                value > labelHash -> high = middle - 1
                else -> return middle
            }
        }
        return -1
    }

    private companion object {
        const val ROOT = 0
    }

    class Builder {
        private class Node {
            val children = HashMap<Long, Node>()
            var terminal = false
        }

        private val root = Node()

        fun add(domain: String) {
            var node = root
            domain.split('.').asReversed().forEach { label ->
                // Stopping at an existing terminal drops sub.example.com when example.com already covers it.
                if (node.terminal) return
                node = node.children.getOrPut(Hashing.fnv64(label)) { Node() }
            }
            node.terminal = true
            node.children.clear()
        }

        fun build(): DomainTrie {
            val hashes = ArrayList<Long>()
            val terminals = ArrayList<Boolean>()
            val starts = ArrayList<Int>()
            val queue = ArrayDeque<Node>()

            hashes += 0L
            terminals += false
            queue += root
            var nextChildId = 1
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                starts += nextChildId
                node.children.entries.sortedBy { it.key }.forEach { (hash, child) ->
                    hashes += hash
                    terminals += child.terminal
                    queue += child
                    nextChildId++
                }
            }
            starts += nextChildId
            return DomainTrie(hashes.toLongArray(), starts.toIntArray(), terminals.toBooleanArray())
        }
    }
}
