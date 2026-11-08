package com.seasaw.theta.feature.adblock.engine

import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Build-time tool that downloads the cookie notice list and writes the pack bundled in assets.
 * Usage: CookiePackCompilerMainKt <output file> <https url>
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: <output file> <https url>" }
    val url = args[1]
    require(url.startsWith("https://")) { "Filter lists must use https: $url" }
    println("Fetching $url")
    val compiler = CookiePackCompiler()
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.setRequestProperty("User-Agent", "Theta-build")
    connection.inputStream.bufferedReader().useLines(compiler::addLines)
    val pack = compiler.build()
    val output = File(args[0])
    output.parentFile?.mkdirs()
    output.outputStream().use { CookiePackIo.write(pack, it) }
    println(
        "Wrote ${output.length()} bytes: ${pack.network.ruleCount} network rules, " +
            "${pack.cosmetic.ruleCount} hiding rules",
    )
}
