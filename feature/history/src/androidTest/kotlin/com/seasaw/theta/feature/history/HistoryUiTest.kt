package com.seasaw.theta.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasaw.theta.core.ui.ThetaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val rows = listOf(
        HistoryRow(1, "https://m.youtube.com/shorts/a", "Short A"),
        HistoryRow(2, "https://m.youtube.com/shorts/b", "Short B"),
        HistoryRow(3, "https://news.example/today", "News"),
    )
    private val events = mutableListOf<HistoryUiEvent>()

    private fun show(state: HistoryUiState) {
        compose.setContent { ThetaTheme { HistoryScreen(state, {}, {}, { events += it }) {} } }
    }

    private fun grouped(expanded: Set<String> = emptySet()) =
        HistoryUiState(rows = rows, groups = HistoryViewModel.groupBySite(rows), expandedSites = expanded)

    @Test
    fun aSiteWithManyVisitsShowsAsOneRowWithACount() {
        show(grouped())
        compose.onNodeWithTag(HistoryTestTags.SITE_PREFIX + "youtube.com").assertIsDisplayed()
        compose.onNodeWithText("2 visits", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Short B").assertDoesNotExist()
        compose.onNodeWithText("News").assertIsDisplayed()
    }

    @Test
    fun anOpenSiteShowsItsVisits() {
        show(grouped(expanded = setOf("youtube.com")))
        compose.onNodeWithText("Short B").assertIsDisplayed()
    }

    @Test
    fun tappingASiteAsksToToggleIt() {
        show(grouped())
        compose.onNodeWithTag(HistoryTestTags.SITE_PREFIX + "youtube.com").performClick()
        assertEquals(listOf<HistoryUiEvent>(HistoryUiEvent.ToggleSite("youtube.com")), events)
    }

    @Test
    fun deletingASiteAsksForConfirmationFirst() {
        show(grouped())
        compose.onNodeWithTag(HistoryTestTags.DELETE_SITE_PREFIX + "youtube.com").performClick()
        assertEquals(emptyList<HistoryUiEvent>(), events)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf<HistoryUiEvent>(HistoryUiEvent.DeleteSite("youtube.com")), events)
    }

    @Test
    fun ungroupedHistoryListsEveryVisit() {
        show(HistoryUiState(rows = rows, isGroupedBySite = false))
        compose.onNodeWithText("Short A").assertIsDisplayed()
        compose.onNodeWithText("Short B").assertIsDisplayed()
        compose.onNodeWithTag(HistoryTestTags.GROUP_TOGGLE).performClick()
        assertEquals(listOf<HistoryUiEvent>(HistoryUiEvent.SetGroupedBySite(true)), events)
    }
}
