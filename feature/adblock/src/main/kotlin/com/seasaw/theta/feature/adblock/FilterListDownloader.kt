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
import java.io.BufferedReader
import java.io.IOException
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
            if (!response.isSuccessful) throw IOException("Filter list request failed with ${response.code}")
            val reader = response.body.charStream().buffered()
            readLimited(reader, addLine)
        }
    }

    /** A misconfigured or hostile URL must not be able to run a budget phone out of memory. */
    private fun readLimited(reader: BufferedReader, addLine: (String) -> Unit) {
        var total = 0L
        reader.lineSequence().forEach { line ->
            total += line.length
            if (total > MAX_LIST_CHARS) throw IOException("Filter list is larger than the limit")
            addLine(line)
        }
    }

    private companion object {
        const val MAX_LIST_CHARS = 16L * 1024 * 1024
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object FilterNetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient()
}
