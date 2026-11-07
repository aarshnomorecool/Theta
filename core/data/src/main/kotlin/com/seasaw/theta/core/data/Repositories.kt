package com.seasaw.theta.core.data

import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.data.db.AdBlockExemptionDao
import com.seasaw.theta.core.data.db.AdBlockExemptionEntity
import com.seasaw.theta.core.data.db.BookmarkDao
import com.seasaw.theta.core.data.db.BookmarkEntity
import com.seasaw.theta.core.data.db.DownloadDao
import com.seasaw.theta.core.data.db.DownloadEntity
import com.seasaw.theta.core.data.db.HistoryDao
import com.seasaw.theta.core.data.db.HistoryEntity
import com.seasaw.theta.core.data.db.SitePermissionDao
import com.seasaw.theta.core.data.db.SitePermissionEntity
import com.seasaw.theta.core.data.db.SpeedDialDao
import com.seasaw.theta.core.data.db.SpeedDialEntity
import com.seasaw.theta.core.data.db.TabDao
import com.seasaw.theta.core.data.db.TabEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Injectable clock, so time-based rules such as history retention can be tested. */
fun interface Clock {
    fun nowMillis(): Long
}

@Singleton
class TabsRepository @Inject constructor(private val dao: TabDao) {
    suspend fun restore(): List<TabEntity> = dao.getAll()

    suspend fun save(tabs: List<TabEntity>) = dao.replaceAll(tabs)

    suspend fun clear() = dao.deleteAll()
}

@Singleton
class HistoryRepository @Inject constructor(private val dao: HistoryDao, private val clock: Clock) {
    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<HistoryEntity>> = dao.observeRecent(limit)

    fun observeMatching(query: String, limit: Int = DEFAULT_LIMIT): Flow<List<HistoryEntity>> =
        dao.observeMatching("%${escapeLike(query)}%", limit)

    /** Only call for regular tabs. Incognito visits must never reach this table. */
    suspend fun recordVisit(url: String, title: String) = dao.recordVisit(url, title, clock.nowMillis())

    suspend fun updateTitle(url: String, title: String) = dao.updateTitle(url, title)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteOlderThan(cutoffMillis: Long): Int = dao.deleteOlderThan(cutoffMillis)

    suspend fun clear() = dao.deleteAll()

    /**
     * Removes every visit to [site], including ones older than the list on screen shows. Matching is done on the parsed
     * site rather than with SQL LIKE, which would also catch other sites whose URLs merely contain this one's name.
     */
    suspend fun deleteSite(site: String): Int {
        val ids = dao.allIdsAndUrls().filter { UrlUtils.siteOf(it.url) == site }.map { it.id }
        // SQLite caps the number of bound parameters in one statement, so large deletions go in batches.
        ids.chunked(DELETE_BATCH_SIZE).forEach { dao.deleteIds(it) }
        return ids.size
    }

    companion object {
        const val DEFAULT_LIMIT = 500
        private const val DELETE_BATCH_SIZE = 500

        /** Stops a user's literal % or _ from acting as a wildcard. Pairs with the DAO's ESCAPE clause. */
        internal fun escapeLike(query: String): String =
            query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}

@Singleton
class BookmarksRepository @Inject constructor(private val dao: BookmarkDao, private val clock: Clock) {
    fun observeAll(): Flow<List<BookmarkEntity>> = dao.observeAll()

    fun observeIsBookmarked(url: String): Flow<Boolean> = dao.observeIsBookmarked(url)

    suspend fun add(url: String, title: String) =
        dao.upsert(BookmarkEntity(url = url, title = title, createdAt = clock.nowMillis()))

    suspend fun remove(url: String) = dao.deleteByUrl(url)

    suspend fun clear() = dao.deleteAll()
}

@Singleton
class SpeedDialRepository @Inject constructor(private val dao: SpeedDialDao) {
    fun observeAll(): Flow<List<SpeedDialEntity>> = dao.observeAll()

    suspend fun add(url: String, title: String) {
        dao.insert(SpeedDialEntity(url = url, title = title, position = dao.nextPosition()))
    }

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun clear() = dao.deleteAll()
}

@Singleton
class DownloadsRepository @Inject constructor(private val dao: DownloadDao, private val clock: Clock) {
    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun record(downloadId: Long, url: String, fileName: String, mimeType: String?) = dao.upsert(
        DownloadEntity(
            downloadId = downloadId,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            createdAt = clock.nowMillis(),
        ),
    )

    suspend fun remove(downloadId: Long) = dao.delete(downloadId)

    suspend fun clear() = dao.deleteAll()
}

@Singleton
class SitePermissionsRepository @Inject constructor(private val dao: SitePermissionDao, private val clock: Clock) {
    fun observeAll(): Flow<List<SitePermissionEntity>> = dao.observeAll()

    /** Returns the remembered answer, or null when the user has not been asked for this site. */
    suspend fun decision(host: String, permission: String): Boolean? = dao.decision(host, permission)

    /** Only call for regular tabs. Incognito never remembers decisions. */
    suspend fun remember(host: String, permission: String, granted: Boolean) =
        dao.upsert(SitePermissionEntity(host, permission, granted, clock.nowMillis()))

    suspend fun forgetHost(host: String) = dao.deleteForHost(host)

    suspend fun clear() = dao.deleteAll()
}

@Singleton
class AdBlockExemptionsRepository @Inject constructor(private val dao: AdBlockExemptionDao, private val clock: Clock) {
    fun observeHosts(): Flow<List<String>> = dao.observeHosts()

    suspend fun exempt(host: String) = dao.upsert(AdBlockExemptionEntity(host, clock.nowMillis()))

    suspend fun removeExemption(host: String) = dao.delete(host)

    suspend fun clear() = dao.deleteAll()
}
