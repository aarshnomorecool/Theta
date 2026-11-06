package com.seasaw.theta.feature.bookmarks

import app.cash.turbine.test
import com.seasaw.theta.core.data.BookmarksRepository
import com.seasaw.theta.core.data.db.BookmarkDao
import com.seasaw.theta.core.data.db.BookmarkEntity
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
class BookmarksViewModelTest {
    private class FakeDao : BookmarkDao {
        val rows = MutableStateFlow(listOf(BookmarkEntity(1, "https://a.example", "Apple", 1)))
        override fun observeAll(): Flow<List<BookmarkEntity>> = rows
        override fun observeIsBookmarked(url: String): Flow<Boolean> = rows.map { list -> list.any { it.url == url } }
        override suspend fun upsert(bookmark: BookmarkEntity) {
            rows.value = rows.value + bookmark
        }

        override suspend fun deleteByUrl(url: String) {
            rows.value = rows.value.filter { it.url != url }
        }

        override suspend fun deleteAll() {
            rows.value = emptyList()
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lists bookmarks and removes them`() = runTest {
        val viewModel = BookmarksViewModel(BookmarksRepository(FakeDao()) { 0L })
        viewModel.state.test {
            assertEquals(listOf("Apple"), expectMostRecentItem().rows.map { it.title })
            viewModel.onEvent(BookmarksUiEvent.Remove("https://a.example"))
            assertEquals(emptyList<BookmarkRow>(), expectMostRecentItem().rows)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
