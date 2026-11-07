package com.seasaw.theta.feature.adblock

import com.seasaw.theta.core.common.AppResult
import com.seasaw.theta.core.common.ApplicationScope
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.config.AppConfig
import com.seasaw.theta.core.data.AdBlockExemptionsRepository
import com.seasaw.theta.core.data.PrivacyStatsRepository
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.webview.InterceptedRequest
import com.seasaw.theta.core.webview.RequestBlocker
import com.seasaw.theta.core.webview.ResourceKind
import com.seasaw.theta.feature.adblock.engine.FilterEngine
import com.seasaw.theta.feature.adblock.engine.FilterSnapshot
import com.seasaw.theta.feature.adblock.engine.ResourceType
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The ad blocker as WebViews see it.
 *
 * [shouldBlock] runs on WebView IO threads and must not wait on anything, so settings and exemptions are mirrored into
 * volatile fields ahead of time. Until the snapshot has loaded, requests are allowed: a few ads on the first page of a
 * cold start are better than a stalled page.
 */
@Singleton
class AdBlocker @Inject constructor(
    private val store: FilterSnapshotStore,
    private val settings: SettingsRepository,
    private val exemptions: AdBlockExemptionsRepository,
    private val config: AppConfig,
    private val stats: PrivacyStatsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : RequestBlocker {
    @Volatile
    private var engine: FilterEngine? = null

    @Volatile
    private var enabledByUser = true

    @Volatile
    private var exemptSites: Set<String> = emptySet()

    private val started = AtomicBoolean(false)

    /** Loads filters in the background. Deferred until after the first frame, per the startup budget. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        stats.start()
        settings.settings.onEach { enabledByUser = it.adBlockEnabled }.launchIn(scope)
        exemptions.observeHosts().onEach { exemptSites = it.toHashSet() }.launchIn(scope)
        scope.launch {
            val loaded = store.load()
            if (loaded is AppResult.Success) use(loaded.value)
        }
    }

    /** Swaps in new filters. In-flight requests finish on the old engine, which is safe as engines are immutable. */
    fun use(snapshot: FilterSnapshot) {
        engine = FilterEngine(snapshot)
    }

    val loadedRuleCount: Int get() = engine?.ruleCount ?: 0

    fun isExempt(pageHost: String): Boolean = UrlUtils.registrableDomain(pageHost) in exemptSites

    /** The per-site "allow ads on this site" toggle. Applies to the whole site, not just one subdomain. */
    suspend fun setSiteExempt(pageHost: String, exempt: Boolean) {
        val site = UrlUtils.registrableDomain(pageHost)
        if (exempt) exemptions.exempt(site) else exemptions.removeExemption(site)
    }

    override fun shouldBlock(request: InterceptedRequest): Boolean {
        val current = engine
        val isActive = current != null && enabledByUser && config.adBlockEnabled
        val host = if (isActive) UrlUtils.hostOf(request.url) else null
        if (current == null || host == null) return false

        val pageHost = request.pageUrl?.let(UrlUtils::hostOf)
        if (pageHost != null && isExempt(pageHost)) return false
        val blocked = current.shouldBlock(
            url = request.url,
            host = host,
            pageUrl = request.pageUrl,
            pageHost = pageHost,
            isThirdParty = UrlUtils.isThirdParty(host, pageHost),
            type = request.kind.toResourceType(),
        )
        // Only a running total is kept, never what was blocked or where.
        if (blocked) stats.recordBlocked()
        return blocked
    }
}

internal fun ResourceKind.toResourceType(): ResourceType = when (this) {
    ResourceKind.DOCUMENT -> ResourceType.DOCUMENT
    ResourceKind.SUBDOCUMENT -> ResourceType.SUBDOCUMENT
    ResourceKind.SCRIPT -> ResourceType.SCRIPT
    ResourceKind.STYLESHEET -> ResourceType.STYLESHEET
    ResourceKind.IMAGE -> ResourceType.IMAGE
    ResourceKind.FONT -> ResourceType.FONT
    ResourceKind.MEDIA -> ResourceType.MEDIA
    ResourceKind.XHR -> ResourceType.XHR
    ResourceKind.PING -> ResourceType.PING
    ResourceKind.OBJECT -> ResourceType.OBJECT
    ResourceKind.OTHER -> ResourceType.OTHER
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AdBlockModule {
    @Binds
    abstract fun requestBlocker(implementation: AdBlocker): RequestBlocker
}
