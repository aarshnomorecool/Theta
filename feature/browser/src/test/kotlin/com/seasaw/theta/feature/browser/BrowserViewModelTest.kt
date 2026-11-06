package com.seasaw.theta.feature.browser

import android.webkit.WebView
import com.seasaw.theta.core.analytics.Analytics
import com.seasaw.theta.core.data.AdBlockExemptionsRepository
import com.seasaw.theta.core.data.BookmarksRepository
import com.seasaw.theta.core.data.DownloadsRepository
import com.seasaw.theta.core.data.EraseRequest
import com.seasaw.theta.core.data.HistoryRepository
import com.seasaw.theta.core.data.LocalDataEraser
import com.seasaw.theta.core.data.SpeedDialRepository
import com.seasaw.theta.core.data.TabsRepository
import com.seasaw.theta.core.data.db.TabEntity
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.data.settings.UserSettings
import com.seasaw.theta.core.webview.DownloadEnqueuer
import com.seasaw.theta.core.webview.ExternalLinks
import com.seasaw.theta.core.webview.IncognitoProfiles
import com.seasaw.theta.core.webview.PermissionPrompt
import com.seasaw.theta.core.webview.SitePermission
import com.seasaw.theta.core.webview.WebDataCleaner
import com.seasaw.theta.core.webview.WebViewFactory
import com.seasaw.theta.core.webview.WebViewPool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val pool = mockk<WebViewPool>(relaxed = true)
    private val tabs = mockk<TabsRepository>(relaxed = true)
    private val history = mockk<HistoryRepository>(relaxed = true)
    private val bookmarks = mockk<BookmarksRepository>(relaxed = true)
    private val exemptions = mockk<AdBlockExemptionsRepository>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val eraser = mockk<LocalDataEraser>(relaxed = true)
    private val webData = mockk<WebDataCleaner>(relaxed = true)
    private val erasures = MutableSharedFlow<EraseRequest>()
    private var savedTabs: List<TabEntity> = emptyList()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settings.settings } returns flowOf(UserSettings())
        every { eraser.erasures } returns erasures
        every { exemptions.observeHosts() } returns flowOf(emptyList())
        every { bookmarks.observeIsBookmarked(any()) } returns flowOf(false)
        coEvery { tabs.restore() } returns emptyList()
        val saved = slot<List<TabEntity>>()
        coEvery { tabs.save(capture(saved)) } answers { savedTabs = saved.captured }
        coEvery { pool.acquire(any(), any(), any()) } returns mockk<WebView>(relaxed = true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BrowserViewModel(
        pool = pool,
        webViewFactory = mockk<WebViewFactory>(relaxed = true),
        tabsRepository = tabs,
        history = history,
        bookmarks = bookmarks,
        speedDial = mockk<SpeedDialRepository>(relaxed = true),
        downloads = mockk<DownloadsRepository>(relaxed = true),
        exemptions = exemptions,
        settings = settings,
        eraser = eraser,
        webDataCleaner = webData,
        incognito = mockk<IncognitoProfiles>(relaxed = true),
        downloadEnqueuer = mockk<DownloadEnqueuer>(relaxed = true),
        externalLinks = mockk<ExternalLinks>(relaxed = true),
        analytics = mockk<Analytics>(relaxed = true),
        appScope = CoroutineScope(dispatcher),
        mainDispatcher = dispatcher,
    )

    private fun BrowserViewModel.selectedId() = checkNotNull(state.value.selectedTabId)

    @Test
    fun `first launch opens one new tab page and no webview`() {
        val vm = viewModel()
        assertEquals(1, vm.state.value.tabs.size)
        assertTrue(vm.state.value.selectedTab!!.isNewTab)
        coVerify(exactly = 0) { pool.acquire(any(), any(), any()) }
    }

    @Test
    fun `regular visits are recorded in history`() {
        val vm = viewModel()
        TabEventsBridge(vm, vm.selectedId()).onNavigated("https://a.example/", canGoBack = false, canGoForward = false)
        coVerify { history.recordVisit("https://a.example/", any()) }
    }

    @Test
    fun `incognito visits and titles never reach history`() {
        val vm = viewModel()
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = true))
        val bridge = TabEventsBridge(vm, vm.selectedId())
        bridge.onNavigated("https://private.example/", canGoBack = false, canGoForward = false)
        bridge.onTitle("Private page")
        coVerify(exactly = 0) { history.recordVisit(any(), any()) }
        coVerify(exactly = 0) { history.updateTitle(any(), any()) }
    }

    @Test
    fun `incognito tabs are never written to the saved tab list`() {
        val vm = viewModel()
        TabEventsBridge(vm, vm.selectedId()).onNavigated("https://a.example/", canGoBack = false, canGoForward = false)
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = true))
        TabEventsBridge(
            vm,
            vm.selectedId(),
        ).onNavigated("https://private.example/", canGoBack = false, canGoForward = false)
        vm.onEvent(BrowserUiEvent.AppBackgrounded)
        assertEquals(listOf("https://a.example/"), savedTabs.map { it.url })
    }

    @Test
    fun `saved tabs come back with their selection`() {
        coEvery { tabs.restore() } returns listOf(
            TabEntity("a", "https://a.example/", "A", 0, false, 0),
            TabEntity("b", "https://b.example/", "B", 1, true, 0),
        )
        val vm = viewModel()
        assertEquals(listOf("a", "b"), vm.state.value.tabs.map { it.id })
        assertEquals("b", vm.state.value.selectedTabId)
    }

    /** Regression test: a link from another app that arrived during restore was overwritten by the restored list. */
    @Test
    fun `a link opened from another app survives restoring tabs`() {
        val vm = viewModel()
        vm.openFromIntent("https://link.example/")
        assertTrue(vm.state.value.tabs.any { it.url == "https://link.example/" })
        assertEquals("https://link.example/", vm.state.value.selectedTab?.url)
    }

    @Test
    fun `an empty new tab is reused rather than piling up`() {
        val vm = viewModel()
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = false))
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = false))
        assertEquals(1, vm.state.value.tabs.size)
    }

    @Test
    fun `closing the last tab leaves a fresh new tab page`() {
        val vm = viewModel()
        val first = vm.selectedId()
        vm.onEvent(BrowserUiEvent.CloseTab(first))
        assertEquals(1, vm.state.value.tabs.size)
        assertFalse(vm.state.value.tabs.single().id == first)
    }

    @Test
    fun `closing incognito tabs goes through the pool so the session is wiped`() {
        val vm = viewModel()
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = true))
        val incognitoId = vm.selectedId()
        vm.onEvent(BrowserUiEvent.CloseIncognitoTabs)
        coVerify { pool.close(incognitoId) }
        assertTrue(vm.state.value.tabs.none { it.isIncognito })
    }

    @Test
    fun `a search goes to the default engine`() {
        val vm = viewModel()
        vm.onEvent(BrowserUiEvent.Submit("best phone"))
        assertEquals("https://www.google.com/search?q=best+phone", vm.state.value.selectedTab?.url)
    }

    @Test
    fun `erasing tabs closes the ones that are open`() {
        val vm = viewModel()
        TabEventsBridge(vm, vm.selectedId()).onNavigated("https://a.example/", canGoBack = false, canGoForward = false)
        kotlinx.coroutines.runBlocking { erasures.emit(EraseRequest.Everything) }
        assertTrue(vm.state.value.selectedTab!!.isNewTab)
    }

    /** A page must not be able to stack dialogs on the user. */
    @Test
    fun `only one prompt shows at a time and later requests are refused`() {
        val vm = viewModel()
        val bridge = TabEventsBridge(vm, vm.selectedId())
        var secondAnswer: Boolean? = null
        bridge.onPermissionPrompt(PermissionPrompt("a.example", setOf(SitePermission.CAMERA)) { _, _ -> })
        bridge.onPermissionPrompt(
            PermissionPrompt("b.example", setOf(SitePermission.CAMERA)) { granted, _ ->
                secondAnswer =
                    granted
            },
        )
        assertEquals("a.example", (vm.state.value.prompt as BrowserPrompt.Permission).prompt.host)
        assertEquals(false, secondAnswer)
    }

    @Test
    fun `a background tab cannot raise a prompt`() {
        val vm = viewModel()
        val background = vm.selectedId()
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = true))
        var answer: Boolean? = null
        TabEventsBridge(vm, background).onPermissionPrompt(
            PermissionPrompt("a.example", setOf(SitePermission.CAMERA)) { granted, _ -> answer = granted },
        )
        assertNull(vm.state.value.prompt)
        assertEquals(false, answer)
    }

    @Test
    fun `answering a prompt clears it and passes on the answer`() {
        val vm = viewModel()
        var granted: Boolean? = null
        var remembered: Boolean? = null
        TabEventsBridge(vm, vm.selectedId()).onPermissionPrompt(
            PermissionPrompt("a.example", setOf(SitePermission.CAMERA)) { g, r ->
                granted = g
                remembered = r
            },
        )
        assertNotNull(vm.state.value.prompt)
        vm.onEvent(BrowserUiEvent.RespondPermission(granted = true, remember = true))
        assertEquals(true, granted)
        assertEquals(true, remembered)
        assertNull(vm.state.value.prompt)
    }
}
