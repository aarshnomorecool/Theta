package com.seasaw.theta.core.data

import javax.inject.Inject
import javax.inject.Singleton

/** Trims old history so the database cannot grow without limit on a phone with little storage. */
@Singleton
class HistoryRetention @Inject constructor(private val history: HistoryRepository, private val clock: Clock) {
    suspend fun trim(): Int = history.deleteOlderThan(clock.nowMillis() - RETENTION_MILLIS)

    private companion object {
        const val RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}
