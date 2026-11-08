package com.seasaw.theta.feature.adblock

import android.content.Context
import com.seasaw.theta.core.common.AppError
import com.seasaw.theta.core.common.AppResult
import com.seasaw.theta.core.common.ApplicationScope
import com.seasaw.theta.core.common.IoDispatcher
import com.seasaw.theta.core.common.resultOf
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.webview.CosmeticFilter
import com.seasaw.theta.core.webview.HiddenSelectors
import com.seasaw.theta.core.webview.InterceptedRequest
import com.seasaw.theta.feature.adblock.engine.CookiePack
import com.seasaw.theta.feature.adblock.engine.CookiePackIo
import com.seasaw.theta.feature.adblock.engine.CosmeticRules
import com.seasaw.theta.feature.adblock.engine.FilterEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the compiled cookie notice list lives: the newest download, or the copy bundled in assets. */
@Singleton
class CookiePackStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val updatedFile = File(context.filesDir, "filters/cookies.bin")

    suspend fun load(): AppResult<CookiePack> = withContext(dispatcher) {
        loadUpdated() ?: resultOf(mapError = { AppError.Storage }) {
            context.assets.open(BUNDLED_ASSET).use(CookiePackIo::read)
        }
    }

    private fun loadUpdated(): AppResult<CookiePack>? {
        if (!updatedFile.exists()) return null
        return try {
            AppResult.Success(updatedFile.inputStream().use(CookiePackIo::read))
        } catch (ignored: IOException) {
            // Most likely written by an older app version with a different format. The bundled one takes over.
            updatedFile.delete()
            null
        }
    }

    /** Writes to a temporary file and renames, so a crash mid-write can never leave a half-written pack. */
    suspend fun save(pack: CookiePack): AppResult<Unit> = withContext(dispatcher) {
        resultOf(mapError = { AppError.Storage }) {
            val directory = checkNotNull(updatedFile.parentFile)
            directory.mkdirs()
            val temporary = File(directory, "cookies.tmp")
            temporary.outputStream().use { CookiePackIo.write(pack, it) }
            if (!temporary.renameTo(updatedFile)) throw IOException("Could not replace the cookie pack")
        }
    }

    private companion object {
        const val BUNDLED_ASSET = "cookies.bin"
    }
}

/**
 * Stops cookie consent pop-ups: blocks the scripts that draw them, and hides the ones that get through. Nothing is
 * ever accepted on the user's behalf.
 *
 * It has its own setting, apart from ad blocking, and loads its list only while that setting is on, so users who turn
 * it off pay no memory for it.
 */
@Singleton
class CookieNoticeBlocker @Inject constructor(
    private val store: CookiePackStore,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : CosmeticFilter {
    @Volatile
    private var network: FilterEngine? = null

    @Volatile
    private var cosmetic: CosmeticRules? = null

    @Volatile
    private var enabledByUser = false

    private val started = AtomicBoolean(false)
    private val loading = AtomicBoolean(false)

    val loadedRuleCount: Int get() = (network?.ruleCount ?: 0) + (cosmetic?.ruleCount ?: 0)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        settings.settings
            .map { it.blockCookieNotices }
            .distinctUntilChanged()
            .onEach { enabled ->
                enabledByUser = enabled
                if (enabled) loadOnce() else unload()
            }
            .launchIn(scope)
    }

    /** Swaps in a newer list. In-flight lookups finish on the old one, which is safe as both are immutable. */
    fun use(pack: CookiePack) {
        if (!enabledByUser) return
        network = FilterEngine(pack.network)
        cosmetic = pack.cosmetic
    }

    private fun loadOnce() {
        if (network != null || !loading.compareAndSet(false, true)) return
        scope.launch {
            val loaded = store.load()
            if (loaded is AppResult.Success) use(loaded.value)
            loading.set(false)
        }
    }

    private fun unload() {
        network = null
        cosmetic = null
    }

    /** Runs on WebView IO threads, so it only reads what is already loaded. */
    fun shouldBlock(request: InterceptedRequest, host: String, pageHost: String?): Boolean {
        val engine = network?.takeIf { enabledByUser } ?: return false
        return engine.shouldBlock(
            url = request.url,
            host = host,
            pageUrl = request.pageUrl,
            pageHost = pageHost,
            isThirdParty = UrlUtils.isThirdParty(host, pageHost),
            type = request.kind.toResourceType(),
        )
    }

    override val isActive: Boolean get() = enabledByUser && cosmetic != null

    override fun selectorsFor(pageUrl: String, ids: List<String>, classes: List<String>): HiddenSelectors {
        val rules = cosmetic?.takeIf { enabledByUser } ?: return HiddenSelectors.Empty
        val host = UrlUtils.hostOf(pageUrl) ?: return HiddenSelectors.Empty
        val all = rules.selectorsFor(host, ids, classes)
        // Generic rules naming an id or class on the page, and site rules, are the ones worth checking for a pop-up.
        val probes = all.filter { it.startsWith('#') || it.startsWith('.') }
        return HiddenSelectors(all, probes)
    }
}
