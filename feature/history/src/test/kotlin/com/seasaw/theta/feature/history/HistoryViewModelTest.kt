package com.seasaw.theta.feature.history

import app.cash.turbine.test
import com.seasaw.theta.core.data.HistoryRepository
import com.seasaw.theta.core.data.db.HistoryDao
import com.seasaw.theta.core.data.db.HistoryEntity
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private class FakeDao : HistoryDao {
        val rows = MutableStateFlow(
            listOf(
                HistoryEntity(1, "https://a.example", "Apple", 2, 1),
                HistoryEntity(2, "https://b.example", "Banana", 1, 1),
            ),
        )

        override fun observeRecent(limit: Int): Flow<List<HistoryEntity>> = rows
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

        override suspend fun deleteOlderThan(before: Long) = 0
        override suspend fun deleteAll() {
            rows.value = emptyList()
        }
    }

    private val dao = FakeDao()
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = HistoryViewModel(HistoryRepository(dao) { 0L })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lists recent history`() = runTest {
        viewModel.state.test {
            assertEquals(listOf("Apple", "Banana"), awaitItem().rows.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searching narrows the list`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onEvent(HistoryUiEvent.QueryChanged("ban"))
            assertEquals(listOf("Banana"), expectMostRecentItem().rows.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting removes one row`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onEvent(HistoryUiEvent.Delete(1))
            assertEquals(listOf("Banana"), expectMostRecentItem().rows.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing empties the list`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onEvent(HistoryUiEvent.ClearAll)
            assertEquals(emptyList<HistoryRow>(), expectMostRecentItem().rows)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
