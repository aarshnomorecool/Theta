package com.seasaw.theta.feature.adblock.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Everything compiled from the cookie notice list: network rules that stop consent pop-up scripts, and element hiding
 * rules for the pop-ups themselves. One file, so both halves always come from the same version of the list.
 */
class CookiePack(val network: FilterSnapshot, val cosmetic: CosmeticRules) {
    val ruleCount: Int get() = network.ruleCount + cosmetic.ruleCount
}

/** Builds a [CookiePack] from list text, at build time and in the weekly worker. */
class CookiePackCompiler {
    private val network = FilterCompiler()
    private val cosmetic = CosmeticCompiler()

    fun addLine(line: String) {
        network.addLine(line)
        cosmetic.addLine(line)
    }

    fun addLines(lines: Sequence<String>) = lines.forEach(::addLine)

    fun build(): CookiePack = CookiePack(network.build(), cosmetic.build())
}

/** Binary pack format. Bump [FORMAT_VERSION] on any layout change: older files are then rejected, not misread. */
object CookiePackIo {
    private const val MAGIC = 0x5448434B // "THCK"
    private const val FORMAT_VERSION = 1
    private const val MAX_ITEMS = 1_000_000
    private const val MAX_BYTES = 64 * 1024 * 1024

    fun write(pack: CookiePack, output: OutputStream) {
        val out = DataOutputStream(output.buffered())
        out.writeInt(MAGIC)
        out.writeInt(FORMAT_VERSION)
        val network = ByteArrayOutputStream().also { SnapshotIo.write(pack.network, it) }.toByteArray()
        out.writeInt(network.size)
        out.write(network)
        val rules = pack.cosmetic
        writeStrings(out, rules.genericIds)
        writeStrings(out, rules.genericClasses)
        writeStrings(out, rules.genericComplex)
        writeMap(out, rules.siteSelectors)
        writeMap(out, rules.siteExceptions)
        out.flush()
    }

    /** @throws IOException when the data is truncated, corrupt or from an incompatible version */
    fun read(input: InputStream): CookiePack {
        val data = DataInputStream(input.buffered())
        if (data.readInt() != MAGIC) throw IOException("Not a cookie pack")
        val version = data.readInt()
        if (version != FORMAT_VERSION) throw IOException("Unsupported cookie pack version $version")
        val networkBytes = ByteArray(readCount(data, MAX_BYTES)).also(data::readFully)
        val network = SnapshotIo.read(ByteArrayInputStream(networkBytes))
        val cosmetic = CosmeticRules(
            genericIds = readStrings(data).toHashSet(),
            genericClasses = readStrings(data).toHashSet(),
            genericComplex = readStrings(data),
            siteSelectors = readMap(data) { it },
            siteExceptions = readMap(data) { it.toHashSet() },
        )
        return CookiePack(network, cosmetic)
    }

    private fun writeStrings(out: DataOutputStream, values: Collection<String>) {
        out.writeInt(values.size)
        values.forEach(out::writeUTF)
    }

    private fun readStrings(data: DataInputStream): List<String> = List(readCount(data, MAX_ITEMS)) { data.readUTF() }

    private fun writeMap(out: DataOutputStream, map: Map<String, Collection<String>>) {
        out.writeInt(map.size)
        map.forEach { (key, values) ->
            out.writeUTF(key)
            writeStrings(out, values)
        }
    }

    private fun <T> readMap(data: DataInputStream, convert: (List<String>) -> T): Map<String, T> {
        val size = readCount(data, MAX_ITEMS)
        val map = HashMap<String, T>(size)
        repeat(size) { map[data.readUTF()] = convert(readStrings(data)) }
        return map
    }

    private fun readCount(data: DataInputStream, max: Int): Int {
        val count = data.readInt()
        if (count < 0 || count > max) throw IOException("Corrupt cookie pack")
        return count
    }
}
