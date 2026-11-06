package com.seasaw.theta.feature.adblock.engine

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/** Binary snapshot format. Bump [FORMAT_VERSION] on any layout change: older files are then rejected, not misread. */
object SnapshotIo {
    private const val MAGIC = 0x54484653 // "THFS"
    private const val FORMAT_VERSION = 1

    fun write(snapshot: FilterSnapshot, output: OutputStream) {
        val out = DataOutputStream(output.buffered())
        out.writeInt(MAGIC)
        out.writeInt(FORMAT_VERSION)
        writeTrie(out, snapshot.domains)
        writeTrie(out, snapshot.thirdPartyDomains)
        writeInts(out, snapshot.rules.flags)
        writeInts(out, snapshot.rules.patternStarts)
        writeBytes(out, snapshot.rules.patternBytes)
        writeInts(out, snapshot.rules.domainStarts)
        writeLongs(out, snapshot.rules.domainHashes)
        writeIndex(out, snapshot.blockIndex)
        writeIndex(out, snapshot.exceptionIndex)
        out.flush()
    }

    /** @throws IOException when the data is truncated, corrupt or from an incompatible version */
    fun read(input: InputStream): FilterSnapshot {
        val data = DataInputStream(input.buffered(BUFFER_SIZE))
        if (data.readInt() != MAGIC) throw IOException("Not a filter snapshot")
        val version = data.readInt()
        if (version != FORMAT_VERSION) throw IOException("Unsupported filter snapshot version $version")
        return FilterSnapshot(
            domains = readTrie(data),
            thirdPartyDomains = readTrie(data),
            rules = RuleTable(
                flags = readInts(data),
                patternStarts = readInts(data),
                patternBytes = readBytes(data),
                domainStarts = readInts(data),
                domainHashes = readLongs(data),
            ),
            blockIndex = readIndex(data),
            exceptionIndex = readIndex(data),
        )
    }

    private fun writeTrie(out: DataOutputStream, trie: DomainTrie) {
        writeLongs(out, trie.labelHashes)
        writeInts(out, trie.childStarts)
        out.writeInt(trie.terminals.size)
        trie.terminals.forEach { out.writeBoolean(it) }
    }

    private fun readTrie(data: DataInputStream): DomainTrie {
        val hashes = readLongs(data)
        val starts = readInts(data)
        val terminals = BooleanArray(readLength(data)).also { array ->
            readBytesOfLength(data, array.size).forEachIndexed { index, value -> array[index] = value != 0.toByte() }
        }
        return DomainTrie(hashes, starts, terminals)
    }

    private fun writeIndex(out: DataOutputStream, index: TokenIndex) {
        writeInts(out, index.slotKeys)
        writeInts(out, index.slotStarts)
        writeInts(out, index.postings)
        writeInts(out, index.genericRules)
    }

    private fun readIndex(data: DataInputStream): TokenIndex =
        TokenIndex(readInts(data), readInts(data), readInts(data), readInts(data))

    private fun writeBytes(out: DataOutputStream, values: ByteArray) {
        out.writeInt(values.size)
        out.write(values)
    }

    private fun readBytes(data: DataInputStream): ByteArray = readBytesOfLength(data, readLength(data))

    private fun writeInts(out: DataOutputStream, values: IntArray) {
        out.writeInt(values.size)
        values.forEach(out::writeInt)
    }

    private fun readInts(data: DataInputStream): IntArray {
        val size = readLength(data)
        val result = IntArray(size)
        ByteBuffer.wrap(readBytesOfLength(data, size * Int.SIZE_BYTES)).asIntBuffer().get(result)
        return result
    }

    private fun writeLongs(out: DataOutputStream, values: LongArray) {
        out.writeInt(values.size)
        values.forEach(out::writeLong)
    }

    private fun readLongs(data: DataInputStream): LongArray {
        val size = readLength(data)
        val result = LongArray(size)
        ByteBuffer.wrap(readBytesOfLength(data, size * Long.SIZE_BYTES)).asLongBuffer().get(result)
        return result
    }

    private fun readLength(data: DataInputStream): Int {
        val size = data.readInt()
        if (size < 0 || size > MAX_ARRAY_LENGTH) throw IOException("Corrupt filter snapshot")
        return size
    }

    private fun readBytesOfLength(data: DataInputStream, size: Int): ByteArray = ByteArray(size).also(data::readFully)

    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_ARRAY_LENGTH = 16 * 1024 * 1024
}
