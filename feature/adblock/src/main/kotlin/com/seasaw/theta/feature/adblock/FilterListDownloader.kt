package com.seasaw.theta.feature.adblock

import com.seasaw.theta.core.common.AppError
import com.seasaw.theta.core.common.AppResult
import com.seasaw.theta.core.common.IoDispatcher
import com.seasaw.theta.core.common.resultOf
import com.seasaw.theta.feature.adblock.engine.CookiePack
import com.seasaw.theta.feature.adblock.engine.CookiePackCompiler
import com.seasaw.theta.feature.adblock.engine.FilterCompiler
import com.seasaw.theta.feature.adblock.engine.FilterSnapshot
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches filter lists and compiles them. All lists must succeed: a partial set would quietly remove blocking that
 * users already have, so one failed list keeps the current snapshot in place until the next attempt.
 */
class FilterListDownloader @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend fun download(urls: List<String>): AppResult<FilterSnapshot> = withContext(dispatcher) {
        resultOf(mapError = { failure ->
            if (failure is IOException) AppError.Network else AppError.Unexpected(failure)
        }) {
            val compiler = FilterCompiler()
            urls.forEach { url -> fetchInto(url, compiler::addLine) }
            compiler.build()
        }
    }

    suspend fun downloadCookiePack(url: String): AppResult<CookiePack> = withContext(dispatcher) {
        resultOf(mapError = { failure ->
            if (failure is IOException) AppError.Network else AppError.Unexpected(failure)
        }) {
            val compiler = CookiePackCompiler()
            fetchInto(url, compiler::addLine)
            compiler.build()
        }
    }

    private fun fetchInto(url: String, addLine: (String) -> Unit) {
        // Lists decide what users can load, so plain http would let anyone on the network rewrite them.
        if (!url.startsWith("https://")) throw IOException("Refusing a filter list that is not https")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val problem = when {
                !response.isSuccessful -> "Filter list request failed with ${response.code}"

                // A redirect may have moved the request. Plain http anywhere along the way defeats the check above.
                !response.request.url.isHttps -> "Filter list was redirected off https"

                else -> null
            }
            if (problem != null) throw IOException(problem)
            val reader = LimitedInputStream(response.body.byteStream(), MAX_LIST_BYTES).bufferedReader()
            reader.lineSequence().forEach(addLine)
        }
    }

    private companion object {
        const val MAX_LIST_BYTES = 16L * 1024 * 1024
    }
}

/**
 * Fails once more than [limit] bytes have been read. Counting bytes rather than lines matters: one enormous line
 * would otherwise be read into memory whole before any per-line check could run, which a misconfigured or hostile
 * URL could use to run a budget phone out of memory.
 */
internal class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) add(1L) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

    override fun skip(n: Long): Long = super.skip(n).also(::add)

    private fun add(bytes: Long) {
        count += bytes
        if (count > limit) throw IOException("Filter list is larger than the limit")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object FilterNetworkModule {
    /** Never follows a redirect from https to http, which OkHttp otherwise allows by default. */
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .followSslRedirects(false)
        .build()
}
