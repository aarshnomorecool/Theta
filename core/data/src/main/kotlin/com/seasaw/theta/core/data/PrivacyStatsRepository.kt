package com.seasaw.theta.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.seasaw.theta.core.common.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Counts only, never which sites or which requests. */
data class PrivacyStats(val blockedRequests: Long, val countingSince: Long?) {
    /** A rough figure for the new-tab page, labelled as an estimate there. */
    val estimatedBytesSaved: Long get() = blockedRequests * AVERAGE_BLOCKED_BYTES
    val estimatedMillisSaved: Long get() = blockedRequests * AVERAGE_BLOCKED_MILLIS

    companion object {
        val Empty = PrivacyStats(0, null)

        /** Typical size of an ad or tracker response on mobile pages. */
        const val AVERAGE_BLOCKED_BYTES = 25L * 1024

        /** Typical time one such request adds on a 4G connection. */
        const val AVERAGE_BLOCKED_MILLIS = 50L
    }
}

/**
 * The privacy stats shown on the new-tab page.
 *
 * They live in their own file, which "delete all my data" deliberately leaves alone: they are a running total, not a
 * record of browsing, and the user asked for them to survive a reset. Settings has a separate reset for them.
 *
 * [recordBlocked] runs on WebView IO threads for every blocked request, so it only bumps memory; the total is written
 * to disk at most every few seconds.
 */
@Singleton
class PrivacyStatsRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock,
    ) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { context.preferencesDataStoreFile("privacy_stats") },
        ),
        scope,
        clock,
    )

    private val counted = MutableStateFlow<PrivacyStats?>(null)

    /** Blocks seen before the saved total finished loading; folded in once it has. */
    private val earlyBlocks = MutableStateFlow(0L)
    private val started = AtomicBoolean(false)
    private val flushScheduled = AtomicBoolean(false)

    val stats: StateFlow<PrivacyStats?> = counted.asStateFlow()

    private val saved: Flow<PrivacyStats> = store.data
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map { PrivacyStats(it[BLOCKED] ?: 0, it[SINCE]) }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val loaded = saved.first()
            val early = earlyBlocks.value
            counted.value = PrivacyStats(
                blockedRequests = loaded.blockedRequests + early,
                countingSince = loaded.countingSince ?: clock.nowMillis().takeIf { early > 0 },
            )
            if (early > 0) scheduleFlush()
        }
    }

    fun recordBlocked() {
        if (counted.value == null) {
            earlyBlocks.update { it + 1 }
            return
        }
        counted.update { current ->
            current?.copy(
                blockedRequests = current.blockedRequests + 1,
                countingSince = current.countingSince ?: clock.nowMillis(),
            )
        }
        scheduleFlush()
    }

    /** The only way these numbers go back to zero. */
    suspend fun reset() {
        counted.value = PrivacyStats.Empty
        store.edit { it.clear() }
    }

    private fun scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(FLUSH_DELAY_MILLIS)
            flushScheduled.set(false)
            val snapshot = counted.value ?: return@launch
            store.edit {
                it[BLOCKED] = snapshot.blockedRequests
                snapshot.countingSince?.let { since -> it[SINCE] = since }
            }
        }
    }

    private companion object {
        val BLOCKED = longPreferencesKey("blocked_requests")
        val SINCE = longPreferencesKey("counting_since")
        const val FLUSH_DELAY_MILLIS = 5_000L
    }
}
