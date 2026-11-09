package com.seasaw.theta

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.seasaw.theta.core.analytics.Analytics
import com.seasaw.theta.core.analytics.CrashReporter
import com.seasaw.theta.core.common.ApplicationScope
import com.seasaw.theta.core.config.AppConfig
import com.seasaw.theta.core.data.HistoryRetention
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.webview.IncognitoProfiles
import com.seasaw.theta.feature.adblock.AdBlocker
import com.seasaw.theta.feature.adblock.FilterUpdateScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Everything that must not delay the first frame. The main Activity calls [run] once it has drawn. Each item here is on
 * the project's list of things to lazy-load: filters, config fetch, and background jobs.
 */
@Singleton
class DeferredStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adBlocker: AdBlocker,
    private val filterUpdates: FilterUpdateScheduler,
    private val incognito: IncognitoProfiles,
    private val config: AppConfig,
    private val settings: SettingsRepository,
    private val analytics: Analytics,
    private val crashReporter: CrashReporter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var hasRun = false

    /** Main thread only, because cleaning up incognito profiles touches the WebView engine. */
    fun run() {
        if (hasRun) return
        hasRun = true

        // A killed process never reached the normal wipe, so its private data has to go now.
        incognito.wipeStaleProfiles()
        adBlocker.start()
        filterUpdates.schedule()
        scheduleCleanup()
        // Already installed in ThetaApplication. Running again wraps any handler an SDK added since then.
        crashReporter.installUncaughtExceptionScrubber()

        settings.settings
            .map { it.diagnosticsConsent }
            .distinctUntilChanged()
            .onEach { consented ->
                analytics.setCollectionEnabled(consented)
                crashReporter.setCollectionEnabled(consented)
            }
            .launchIn(scope)
        scope.launch { config.refresh() }
    }

    private fun scheduleCleanup() {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(CLEANUP_INTERVAL_DAYS, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(CLEANUP_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val CLEANUP_WORK_NAME = "local_data_cleanup"
        const val CLEANUP_INTERVAL_DAYS = 7L
    }
}

/** Keeps history from growing forever on phones with little storage. */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val retention: HistoryRetention,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        retention.trim()
        return Result.success()
    }
}
