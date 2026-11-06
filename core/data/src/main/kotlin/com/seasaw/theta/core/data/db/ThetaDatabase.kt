package com.seasaw.theta.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        TabEntity::class,
        HistoryEntity::class,
        BookmarkEntity::class,
        SpeedDialEntity::class,
        DownloadEntity::class,
        SitePermissionEntity::class,
        AdBlockExemptionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ThetaDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun speedDialDao(): SpeedDialDao
    abstract fun downloadDao(): DownloadDao
    abstract fun sitePermissionDao(): SitePermissionDao
    abstract fun adBlockExemptionDao(): AdBlockExemptionDao
}

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ThetaDatabase =
        Room.databaseBuilder(context, ThetaDatabase::class.java, "theta.db").build()

    @Provides fun tabDao(database: ThetaDatabase): TabDao = database.tabDao()

    @Provides fun historyDao(database: ThetaDatabase): HistoryDao = database.historyDao()

    @Provides fun bookmarkDao(database: ThetaDatabase): BookmarkDao = database.bookmarkDao()

    @Provides fun speedDialDao(database: ThetaDatabase): SpeedDialDao = database.speedDialDao()

    @Provides fun downloadDao(database: ThetaDatabase): DownloadDao = database.downloadDao()

    @Provides fun sitePermissionDao(database: ThetaDatabase): SitePermissionDao = database.sitePermissionDao()

    @Provides fun adBlockExemptionDao(database: ThetaDatabase): AdBlockExemptionDao = database.adBlockExemptionDao()
}
