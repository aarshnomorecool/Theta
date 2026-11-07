package com.seasaw.theta.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An open tab. Incognito tabs never get a row: they must not survive the process. */
@Entity(tableName = "tabs", indices = [Index("position")])
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val position: Int,
    val isSelected: Boolean,
    val lastAccessedAt: Long,
    /** The group this tab belongs to, if any. Added in schema version 2. */
    @ColumnInfo(defaultValue = "NULL") val groupId: String? = null,
)

/** A named, coloured set of tabs. [colorIndex] picks from a fixed palette, so themes can tune the actual colours. */
@Entity(tableName = "tab_groups")
data class TabGroupEntity(@PrimaryKey val id: String, val name: String, val colorIndex: Int, val createdAt: Long)

@Entity(
    tableName = "history",
    indices = [Index(value = ["url"], unique = true), Index("visitedAt")],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
    val visitCount: Int,
)

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
)

@Entity(tableName = "speed_dial", indices = [Index(value = ["url"], unique = true), Index("position")])
data class SpeedDialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val position: Int,
)

/** A file handed to the system DownloadManager. [downloadId] is the id it assigned. */
@Entity(tableName = "downloads", indices = [Index("createdAt")])
data class DownloadEntity(
    @PrimaryKey val downloadId: Long,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val createdAt: Long,
)

/** A remembered per-site answer for camera, microphone or location. Never written for incognito tabs. */
@Entity(tableName = "site_permissions", primaryKeys = ["host", "permission"])
data class SitePermissionEntity(val host: String, val permission: String, val granted: Boolean, val updatedAt: Long)

/** A site where the user chose to allow ads. */
@Entity(tableName = "adblock_exemptions")
data class AdBlockExemptionEntity(@PrimaryKey val host: String, val createdAt: Long)
