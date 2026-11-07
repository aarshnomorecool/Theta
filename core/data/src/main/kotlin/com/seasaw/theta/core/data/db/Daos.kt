package com.seasaw.theta.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun observeAll(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY position ASC")
    suspend fun getAll(): List<TabEntity>

    @Upsert
    suspend fun upsert(tab: TabEntity)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tabs")
    suspend fun deleteAll()

    @Query("SELECT * FROM tab_groups ORDER BY createdAt ASC")
    suspend fun getAllGroups(): List<TabGroupEntity>

    @Upsert
    suspend fun upsertGroup(group: TabGroupEntity)

    @Query("DELETE FROM tab_groups")
    suspend fun deleteAllGroups()

    /** Replaces tabs and groups in one transaction, so a crash cannot leave a half-saved tab strip. */
    @Transaction
    suspend fun replaceAll(tabs: List<TabEntity>, groups: List<TabGroupEntity>) {
        deleteAll()
        deleteAllGroups()
        groups.forEach { upsertGroup(it) }
        tabs.forEach { upsert(it) }
    }
}

data class HistoryIdAndUrl(val id: Long, val url: String)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    /** The user's own most visited pages, for the new-tab page's top sites. */
    @Query("SELECT * FROM history ORDER BY visitCount DESC, visitedAt DESC LIMIT :limit")
    fun observeMostVisited(limit: Int): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE url LIKE :pattern ESCAPE '\\' OR title LIKE :pattern ESCAPE '\\' " +
            "ORDER BY visitedAt DESC LIMIT :limit",
    )
    fun observeMatching(pattern: String, limit: Int): Flow<List<HistoryEntity>>

    @Query("UPDATE history SET title = :title, visitedAt = :visitedAt, visitCount = visitCount + 1 WHERE url = :url")
    suspend fun updateExisting(url: String, title: String, visitedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: HistoryEntity): Long

    /**
     * Written as update-then-insert because SQL UPSERT needs SQLite 3.24, and minSdk 26 ships 3.18.
     */
    @Transaction
    suspend fun recordVisit(url: String, title: String, visitedAt: Long) {
        if (updateExisting(url, title, visitedAt) == 0) {
            insert(HistoryEntity(url = url, title = title, visitedAt = visitedAt, visitCount = 1))
        }
    }

    @Query("UPDATE history SET title = :title WHERE url = :url")
    suspend fun updateTitle(url: String, title: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    /** Only ids and URLs, so finding one site's visits does not load every title into memory. */
    @Query("SELECT id, url FROM history")
    suspend fun allIdsAndUrls(): List<HistoryIdAndUrl>

    @Query("DELETE FROM history WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM history WHERE visitedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dial ORDER BY position ASC")
    fun observeAll(): Flow<List<SpeedDialEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM speed_dial")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: SpeedDialEntity): Long

    @Query("DELETE FROM speed_dial WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM speed_dial")
    suspend fun deleteAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE downloadId = :downloadId")
    suspend fun delete(downloadId: Long)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}

@Dao
interface SitePermissionDao {
    @Query("SELECT granted FROM site_permissions WHERE host = :host AND permission = :permission")
    suspend fun decision(host: String, permission: String): Boolean?

    @Upsert
    suspend fun upsert(entry: SitePermissionEntity)

    @Query("SELECT * FROM site_permissions ORDER BY host ASC")
    fun observeAll(): Flow<List<SitePermissionEntity>>

    @Query("DELETE FROM site_permissions WHERE host = :host")
    suspend fun deleteForHost(host: String)

    @Query("DELETE FROM site_permissions")
    suspend fun deleteAll()
}

@Dao
interface AdBlockExemptionDao {
    @Query("SELECT host FROM adblock_exemptions")
    fun observeHosts(): Flow<List<String>>

    @Upsert
    suspend fun upsert(entry: AdBlockExemptionEntity)

    @Query("DELETE FROM adblock_exemptions WHERE host = :host")
    suspend fun delete(host: String)

    @Query("DELETE FROM adblock_exemptions")
    suspend fun deleteAll()
}
