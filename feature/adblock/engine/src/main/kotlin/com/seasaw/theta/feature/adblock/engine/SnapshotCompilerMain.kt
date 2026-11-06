package com.seasaw.theta.feature.adblock.engine

import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Build-time tool that downloads filter lists and writes the snapshot bundled in assets.
 * Usage: SnapshotCompilerMainKt <output file> <https url>...
 */
fun main(args: Array<String>) {
    require(args.size >= 2) { "Usage: <output file> <https url>..." }
    val compiler = FilterCompiler()
    args.drop(1).forEach { url ->
        require(url.startsWith("https://")) { "Filter lists must use https: $url" }
        println("Fetching $url")
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Theta-build")
        connection.inputStream.bufferedReader().useLines(compiler::addLines)
    }
    val snapshot = compiler.build()
    val output = File(args[0])
    output.parentFile?.mkdirs()
    output.outputStream().use { SnapshotIo.write(snapshot, it) }
    println("Wrote ${output.length()} bytes with ${snapshot.ruleCount} rules, ${snapshot.genericRuleCount} generic")
}
