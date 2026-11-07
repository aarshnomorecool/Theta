package com.seasaw.theta.feature.history

import app.cash.turbine.test
import com.seasaw.theta.core.data.HistoryRepository
import com.seasaw.theta.core.data.db.HistoryDao
import com.seasaw.theta.core.data.db.HistoryEntity
import com.seasaw.theta.core.data.db.HistoryIdAndUrl
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.data.settings.UserSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private class FakeDao : HistoryDao {
        val rows = MutableStateFlow(
            listOf(
                HistoryEntity(1, "https://m.youtube.com/shorts/a", "Short A", 5, 1),
                HistoryEntity(2, "https://news.example/today", "News", 4, 1),
                HistoryEntity(3, "https://m.youtube.com/shorts/b", "Short B", 3, 1),
                HistoryEntity(4, "https://www.youtube.com/watch?v=c", "Video C", 2, 1),
            ),
        )

        override fun observeRecent(limit: Int): Flow<List<HistoryEntity>> = rows
        override fun observeMostVisited(limit: Int): Flow<List<HistoryEntity>> = rows
        override fun observeMatching(pattern: String, limit: Int): Flow<List<HistoryEntity>> {
            val needle = pattern.trim('%')
            return rows.map { list -> list.filter { it.title.contains(needle, ignoreCase = true) } }
        }

        override suspend fun updateExisting(url: String, title: String, visitedAt: Long) = 0
        override suspend fun insert(entry: HistoryEntity) = 0L
        override suspend fun updateTitle(url: String, title: String) = Unit
        override suspend fun delete(id: Long) {
            rows.value = rows.value.filter { it.id != id }
        }

        override suspend fun allIdsAndUrls() = rows.value.map { HistoryIdAndUrl(it.id, it.url) }
        override suspend fun deleteIds(ids: List<Long>) {
            rows.value = rows.value.filter { it.id !in ids }
        }

        override suspend fun deleteOlderThan(before: Long) = 0
        override suspend fun deleteAll() {
            rows.value = emptyList()
        }
    }

    private val dao = FakeDao()
    private val userSettings = MutableStateFlow(UserSettings())
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.settings } returns userSettings
        coEvery { settings.setHistoryGroupedBySite(any()) } answers {
            userSettings.value = userSettings.value.copy(historyGroupedBySite = firstArg())
        }
        viewModel = HistoryViewModel(HistoryRepository(dao) { 0L }, settings)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `visits to one site collapse into a single group, newest first`() {
        val groups = HistoryViewModel.groupBySite(
            dao.rows.value.map { HistoryRow(it.id, it.url, it.title) },
        )
        assertEquals(listOf("youtube.com", "news.example"), groups.map { it.site })
        assertEquals(listOf("Short A", "Short B", "Video C"), groups.first().rows.map { it.title })
        assertEquals("Short A", groups.first().latest.title)
    }

    @Test
    fun `pages without a site are never merged together`() {
        val groups = HistoryViewModel.groupBySite(
            listOf(HistoryRow(1, "about:blank", "Blank"), HistoryRow(2, "data:text/html,x", "Data")),
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun `history is grouped by default`() = runTest {
        viewModel.state.test {
            val state = expectMostRecentItem()
            assertTrue(state.isGroupedBySite)
            assertEquals(2, state.groups.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning grouping off shows every visit and is remembered`() = runTest {
        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onEvent(HistoryUiEvent.SetGroupedBySite(false))
            val state = expectMostRecentItem()
            assertFalse(state.isGroupedBySite)
            assertEquals(4, state.rows.size)
            assertFalse(userSettings.value.historyGroupedBySite)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a site opens and closes`() = runTest {
        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onEvent(HistoryUiEvent.ToggleSite("youtube.com"))
            assertEquals(setOf("youtube.com"), expectMostRecentItem().expandedSites)
            viewModel.onEvent(HistoryUiEvent.ToggleSite("youtube.com"))
            assertTrue(expectMostRecentItem().expandedSites.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a site removes all its visits and keeps the rest`() = runTest {
        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onEvent(HistoryUiEvent.DeleteSite("youtube.com"))
            assertEquals(listOf("News"), expectMostRecentItem().rows.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searching narrows the list`() = runTest {
        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onEvent(HistoryUiEvent.QueryChanged("news"))
            assertEquals(listOf("News"), expectMostRecentItem().rows.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting one visit removes only that row`() = runTest {
        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onEvent(HistoryUiEvent.Delete(2))
            assertEquals(3, expectMostRecentItem().rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing empties the list`() = runTest {
        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onEvent(HistoryUiEvent.ClearAll)
            assertTrue(expectMostRecentItem().isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
