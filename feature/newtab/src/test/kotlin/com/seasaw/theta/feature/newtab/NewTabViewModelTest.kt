package com.seasaw.theta.feature.newtab

import org.junit.Assert.assertEquals
import org.junit.Test

class NewTabViewModelTest {
    private fun site(url: String) = RecentSite(url, url)

    @Test
    fun `several pages from one site count once and the newest wins`() {
        val sites = listOf(
            site("https://news.example.in/latest"),
            site("https://www.example.in/sports"),
            site("https://other.example/"),
        )
        assertEquals(
            listOf("https://news.example.in/latest", "https://other.example/"),
            NewTabViewModel.distinctBySite(sites).map { it.url },
        )
    }

    @Test
    fun `the list is capped so the new tab page stays short`() {
        val sites = (1..20).map { site("https://site$it.example/") }
        assertEquals(NewTabViewModel.MAX_RECENT_SITES, NewTabViewModel.distinctBySite(sites).size)
    }
}
