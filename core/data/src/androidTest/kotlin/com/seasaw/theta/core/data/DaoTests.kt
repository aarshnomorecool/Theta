package com.seasaw.theta.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.seasaw.theta.core.data.db.AdBlockExemptionEntity
import com.seasaw.theta.core.data.db.BookmarkEntity
import com.seasaw.theta.core.data.db.DownloadEntity
import com.seasaw.theta.core.data.db.SitePermissionEntity
import com.seasaw.theta.core.data.db.SpeedDialEntity
import com.seasaw.theta.core.data.db.TabEntity
import com.seasaw.theta.core.data.db.ThetaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTests {
    private lateinit var db: ThetaDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ThetaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun tabsReplaceAllIsOrderedAndAtomic() = runTest {
        val dao = db.tabDao()
        dao.replaceAll(listOf(tab("a", position = 1), tab("b", position = 0)))
        assertEquals(listOf("b", "a"), dao.getAll().map { it.id })

        dao.replaceAll(listOf(tab("c", position = 0)))
        assertEquals(listOf("c"), dao.getAll().map { it.id })

        dao.delete("c")
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun historyRecordVisitInsertsThenIncrements() = runTest {
        val dao = db.historyDao()
        dao.recordVisit("https://a.example", "First", 10)
        dao.recordVisit("https://a.example", "Second", 20)

        val rows = dao.observeRecent(10).first()
        assertEquals(1, rows.size)
        assertEquals(2, rows.single().visitCount)
        assertEquals("Second", rows.single().title)
        assertEquals(20L, rows.single().visitedAt)
    }

    @Test
    fun historyIsNewestFirstAndRespectsLimit() = runTest {
        val dao = db.historyDao()
        dao.recordVisit("https://old.example", "old", 1)
        dao.recordVisit("https://new.example", "new", 2)

        assertEquals(listOf("new", "old"), dao.observeRecent(10).first().map { it.title })
        assertEquals(listOf("new"), dao.observeRecent(1).first().map { it.title })
    }

    @Test
    fun historySearchTreatsWildcardsLiterally() = runTest {
        val dao = db.historyDao()
        dao.recordVisit("https://a.example/100%off", "sale", 1)
        dao.recordVisit("https://b.example/100xoff", "other", 2)

        val pattern = "%${HistoryRepository.escapeLike("100%")}%"
        assertEquals(listOf("sale"), dao.observeMatching(pattern, 10).first().map { it.title })
    }

    @Test
    fun historyTrimRemovesOnlyOldRows() = runTest {
        val dao = db.historyDao()
        dao.recordVisit("https://old.example", "old", 1)
        dao.recordVisit("https://new.example", "new", 100)

        assertEquals(1, dao.deleteOlderThan(50))
        assertEquals(listOf("new"), dao.observeRecent(10).first().map { it.title })
    }

    @Test
    fun bookmarkStateIsObservable() = runTest {
        val dao = db.bookmarkDao()
        val url = "https://a.example"
        dao.observeIsBookmarked(url).test {
            assertFalse(awaitItem())
            dao.upsert(BookmarkEntity(url = url, title = "A", createdAt = 1))
            assertTrue(awaitItem())
            dao.deleteByUrl(url)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun speedDialPositionsAppendAndDuplicatesAreIgnored() = runTest {
        val dao = db.speedDialDao()
        assertEquals(0, dao.nextPosition())
        dao.insert(SpeedDialEntity(url = "https://a.example", title = "A", position = 0))
        assertEquals(1, dao.nextPosition())
        dao.insert(SpeedDialEntity(url = "https://a.example", title = "dup", position = 1))
        assertEquals(listOf("A"), dao.observeAll().first().map { it.title })
    }

    @Test
    fun downloadsAreNewestFirst() = runTest {
        val dao = db.downloadDao()
        dao.upsert(DownloadEntity(1, "https://a.example/a", "a", null, createdAt = 1))
        dao.upsert(DownloadEntity(2, "https://a.example/b", "b", "text/plain", createdAt = 2))
        assertEquals(listOf(2L, 1L), dao.observeAll().first().map { it.downloadId })

        dao.delete(2)
        assertEquals(listOf(1L), dao.observeAll().first().map { it.downloadId })
    }

    @Test
    fun sitePermissionsArePerHostAndPermission() = runTest {
        val dao = db.sitePermissionDao()
        assertNull(dao.decision("a.example", "camera"))

        dao.upsert(SitePermissionEntity("a.example", "camera", granted = true, updatedAt = 1))
        dao.upsert(SitePermissionEntity("a.example", "microphone", granted = false, updatedAt = 1))
        assertEquals(true, dao.decision("a.example", "camera"))
        assertEquals(false, dao.decision("a.example", "microphone"))
        assertNull(dao.decision("b.example", "camera"))

        dao.upsert(SitePermissionEntity("a.example", "camera", granted = false, updatedAt = 2))
        assertEquals(false, dao.decision("a.example", "camera"))

        dao.deleteForHost("a.example")
        assertNull(dao.decision("a.example", "microphone"))
    }

    @Test
    fun adBlockExemptionsAddAndRemove() = runTest {
        val dao = db.adBlockExemptionDao()
        dao.upsert(AdBlockExemptionEntity("a.example", createdAt = 1))
        assertEquals(listOf("a.example"), dao.observeHosts().first())

        dao.delete("a.example")
        assertTrue(dao.observeHosts().first().isEmpty())
    }

    private fun tab(id: String, position: Int) = TabEntity(
        id = id,
        url = "https://$id.example",
        title = id,
        position = position,
        isSelected = false,
        lastAccessedAt = 0,
    )
}
