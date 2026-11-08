package com.seasaw.theta.feature.adblock

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.seasaw.theta.core.analytics.Analytics
import com.seasaw.theta.core.analytics.AnalyticsEvent
import com.seasaw.theta.core.common.AppError
import com.seasaw.theta.core.common.AppResult
import com.seasaw.theta.core.config.AppConfig
import com.seasaw.theta.feature.adblock.engine.FilterSnapshot
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class FilterUpdateOutcome { UPDATED, REJECTED, FAILED }

/**
 * Accepts or rejects a downloaded snapshot. A sharply smaller list almost certainly means something went wrong
 * upstream, and installing it would silently turn most blocking off, so this compares with what is running now.
 */
internal object SnapshotValidator {
    private const val MIN_RULES = 10_000
    private const val MIN_FRACTION_OF_CURRENT = 0.5

    fun isAcceptable(newRuleCount: Int, currentRuleCount: Int, minRules: Int = MIN_RULES): Boolean =
        newRuleCount >= minRules && newRuleCount >= currentRuleCount * MIN_FRACTION_OF_CURRENT

    /** The cookie list is much smaller than the ad lists; today it has about 25,000 rules. */
    const val MIN_COOKIE_RULES = 5_000
}

/** One update attempt. Separate from the worker so that it can be tested without WorkManager. */
class FilterUpdater @Inject constructor(
    private val downloader: FilterListDownloader,
    private val store: FilterSnapshotStore,
    private val blocker: AdBlocker,
    private val config: AppConfig,
    private val cookieStore: CookiePackStore,
    private val cookies: CookieNoticeBlocker,
) {
    /** The ad lists decide the outcome. The cookie list is refreshed alongside, and on failure simply waits a week. */
    suspend fun update(): FilterUpdateOutcome {
        val outcome = updateAdLists()
        updateCookieList()
        return outcome
    }

    private suspend fun updateCookieList() {
        val pack = (downloader.downloadCookiePack(config.cookieListUrl) as? AppResult.Success)?.value ?: return
        val acceptable = SnapshotValidator.isAcceptable(
            newRuleCount = pack.ruleCount,
            currentRuleCount = cookies.loadedRuleCount,
            minRules = SnapshotValidator.MIN_COOKIE_RULES,
        )
        if (acceptable && cookieStore.save(pack) is AppResult.Success) cookies.use(pack)
    }

    private suspend fun updateAdLists(): FilterUpdateOutcome {
        val snapshot = when (val downloaded = downloader.download(config.filterListUrls)) {
            is AppResult.Failure -> return outcomeOf(downloaded.error)
            is AppResult.Success -> downloaded.value
        }
        if (!SnapshotValidator.isAcceptable(snapshot.ruleCount, blocker.loadedRuleCount)) {
            return FilterUpdateOutcome.REJECTED
        }
        return install(snapshot)
    }

    private suspend fun install(snapshot: FilterSnapshot): FilterUpdateOutcome = when (store.save(snapshot)) {
        is AppResult.Failure -> FilterUpdateOutcome.FAILED

        is AppResult.Success -> {
            blocker.use(snapshot)
            FilterUpdateOutcome.UPDATED
        }
    }

    private fun outcomeOf(error: AppError): FilterUpdateOutcome = when (error) {
        AppError.InvalidInput -> FilterUpdateOutcome.REJECTED
        else -> FilterUpdateOutcome.FAILED
    }
}

@HiltWorker
class FilterUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val updater: FilterUpdater,
    private val analytics: Analytics,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = when (updater.update()) {
        FilterUpdateOutcome.UPDATED -> {
            analytics.track(AnalyticsEvent.FilterListUpdated("success"))
            Result.success()
        }

        // Retrying now would fetch the same bad list. Next week's run gets a fresh chance.
        FilterUpdateOutcome.REJECTED -> {
            analytics.track(AnalyticsEvent.FilterListUpdated("rejected"))
            Result.success()
        }

        FilterUpdateOutcome.FAILED -> {
            analytics.track(AnalyticsEvent.FilterListUpdated("failure"))
            Result.retry()
        }
    }
}

/** Schedules the weekly refresh. Unmetered only, because users on cheap data must never pay for this. */
@Singleton
class FilterUpdateScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<FilterUpdateWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(INTERVAL_DAYS, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "filter_list_update"
        const val INTERVAL_DAYS = 7L
    }
}
