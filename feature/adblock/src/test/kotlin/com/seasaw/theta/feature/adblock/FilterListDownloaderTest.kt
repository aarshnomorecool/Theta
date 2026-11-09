package com.seasaw.theta.feature.adblock

import com.seasaw.theta.core.common.AppError
import com.seasaw.theta.core.common.AppResult
import java.io.IOException
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FilterListDownloaderTest {
    private val server = MockWebServer()
    private lateinit var downloader: FilterListDownloader

    @Before
    fun setUp() {
        // The downloader refuses plain http, so the happy path has to run over real TLS against a trusted test CA.
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName(InetAddress.getByName("localhost").canonicalHostName)
            .build()
        val serverSide = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientSide = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server.useHttps(serverSide.sslSocketFactory())
        server.start()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientSide.sslSocketFactory(), clientSide.trustManager)
            .build()
        downloader = FilterListDownloader(client, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int = 200, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private fun url(path: String) = server.url(path).toString()

    @Test
    fun `lists are downloaded and compiled together`() = runTest {
        respond(body = "! comment\n||ads.example^\n/banner.\$image\n")
        respond(body = "||tracker.example^\$third-party\n")

        val result = downloader.download(listOf(url("/a.txt"), url("/b.txt")))

        assertEquals(3, (result as AppResult.Success).value.ruleCount)
    }

    @Test
    fun `plain http lists are refused without any request`() = runTest {
        val insecure = url("/list.txt").replaceFirst("https://", "http://")
        assertEquals(AppResult.Failure(AppError.Network), downloader.download(listOf(insecure)))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a server error fails the update`() = runTest {
        respond(code = 500)
        assertEquals(AppResult.Failure(AppError.Network), downloader.download(listOf(url("/a.txt"))))
    }

    @Test
    fun `one failed list fails the whole update`() = runTest {
        respond(body = "||ads.example^\n")
        respond(code = 404)
        val result = downloader.download(listOf(url("/a.txt"), url("/b.txt")))
        assertEquals(AppResult.Failure(AppError.Network), result)
    }

    @Test
    fun `an oversized list is refused`() = runTest {
        val line = "x".repeat(1024) + "\n"
        respond(body = line.repeat(17 * 1024))
        assertEquals(AppResult.Failure(AppError.Network), downloader.download(listOf(url("/huge.txt"))))
    }

    /** Regression test: OkHttp follows https-to-http redirects by default, letting a network attacker swap a list. */
    @Test
    fun `a list redirected to plain http is refused`() = runTest {
        MockWebServer().use { plain ->
            plain.start()
            plain.enqueue(MockResponse.Builder().body("||ads.example^\n").build())
            server.enqueue(
                MockResponse.Builder().code(302).addHeader("Location", plain.url("/list.txt").toString()).build(),
            )
            assertEquals(AppResult.Failure(AppError.Network), downloader.download(listOf(url("/list.txt"))))
        }
    }

    /** Regression test: the limit was checked per line, so one endless line was read whole before any check. */
    @Test
    fun `the byte limit stops a single endless line`() {
        val endless = LimitedInputStream(ByteArray(4096) { 'x'.code.toByte() }.inputStream(), limit = 1024)
        assertThrows(IOException::class.java) { endless.bufferedReader().readLine() }
    }

    @Test
    fun `validator rejects suspiciously small lists`() {
        assertFalse(SnapshotValidator.isAcceptable(newRuleCount = 500, currentRuleCount = 0))
        assertFalse(SnapshotValidator.isAcceptable(newRuleCount = 20_000, currentRuleCount = 100_000))
    }

    @Test
    fun `validator accepts healthy lists`() {
        assertTrue(SnapshotValidator.isAcceptable(newRuleCount = 90_000, currentRuleCount = 100_000))
        assertTrue(SnapshotValidator.isAcceptable(newRuleCount = 120_000, currentRuleCount = 100_000))
    }
}
