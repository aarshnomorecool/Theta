package com.seasaw.theta.feature.browser

import android.webkit.WebView
import com.seasaw.theta.core.analytics.Analytics
import com.seasaw.theta.core.data.AdBlockExemptionsRepository
import com.seasaw.theta.core.data.BookmarksRepository
import com.seasaw.theta.core.data.DownloadsRepository
import com.seasaw.theta.core.data.EraseRequest
import com.seasaw.theta.core.data.FaviconStore
import com.seasaw.theta.core.data.HistoryRepository
import com.seasaw.theta.core.data.LocalDataEraser
import com.seasaw.theta.core.data.SpeedDialRepository
import com.seasaw.theta.core.data.TabPreviewStore
import com.seasaw.theta.core.data.TabsRepository
import com.seasaw.theta.core.data.db.TabEntity
import com.seasaw.theta.core.data.db.TabGroupEntity
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
    private var savedGroups: List<TabGroupEntity> = emptyList()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settings.settings } returns flowOf(UserSettings())
        every { eraser.erasures } returns erasures
        every { exemptions.observeHosts() } returns flowOf(emptyList())
        every { bookmarks.observeIsBookmarked(any()) } returns flowOf(false)
        coEvery { tabs.restore() } returns TabsRepository.Saved(emptyList(), emptyList())
        val saved = slot<List<TabEntity>>()
        val groups = slot<List<TabGroupEntity>>()
        coEvery { tabs.save(capture(saved), capture(groups)) } answers {
            savedTabs = saved.captured
            savedGroups = groups.captured
        }
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
        previewStore = previews,
        faviconStore = favicons,
        appScope = CoroutineScope(dispatcher),
        mainDispatcher = dispatcher,
    )

    private val previews = mockk<TabPreviewStore>(relaxed = true)
    private val favicons = mockk<FaviconStore>(relaxed = true)

    private fun BrowserViewModel.selectedId() = checkNotNull(state.value.selectedTabId)

    private fun BrowserViewModel.visit(url: String) =
        TabEventsBridge(this, selectedId()).onNavigated(url, canGoBack = false, canGoForward = false)

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
        coEvery { tabs.restore() } returns TabsRepository.Saved(
            listOf(
                TabEntity("a", "https://a.example/", "A", 0, false, 0),
                TabEntity("b", "https://b.example/", "B", 1, true, 0),
            ),
            emptyList(),
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
        vm.onEvent(BrowserUiEvent.CloseAllTabs(isIncognito = true))
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

    @Test
    fun `groups are saved with their regular tabs and come back after a restart`() {
        val vm = viewModel()
        vm.visit("https://a.example/")
        val first = vm.selectedId()
        vm.onEvent(BrowserUiEvent.CreateGroup(setOf(first), "Cricket", 2, isIncognito = false))
        vm.onEvent(BrowserUiEvent.AppBackgrounded)

        assertEquals(listOf("Cricket"), savedGroups.map { it.name })
        val groupId = savedGroups.single().id
        assertEquals(listOf(groupId), savedTabs.map { it.groupId })

        coEvery { tabs.restore() } returns TabsRepository.Saved(savedTabs, savedGroups)
        val restored = viewModel()
        assertEquals(listOf(TabGroup(groupId, "Cricket", 2)), restored.state.value.groups)
        assertEquals(groupId, restored.state.value.tabs.single().groupId)
    }

    @Test
    fun `a saved tab pointing at a missing group comes back ungrouped`() {
        coEvery { tabs.restore() } returns TabsRepository.Saved(
            listOf(TabEntity("a", "https://a.example/", "A", 0, true, 0, groupId = "gone")),
            emptyList(),
        )
        val vm = viewModel()
        assertNull(vm.state.value.tabs.single().groupId)
        assertTrue(vm.state.value.groups.isEmpty())
    }

    @Test
    fun `private groups are never written to disk`() {
        val vm = viewModel()
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = true))
        vm.visit("https://private.example/")
        vm.onEvent(BrowserUiEvent.CreateGroup(setOf(vm.selectedId()), "Secret", 0, isIncognito = true))
        vm.onEvent(BrowserUiEvent.AppBackgrounded)
        assertEquals(1, vm.state.value.groups.size)
        assertTrue(savedGroups.isEmpty())
    }

    @Test
    fun `a group disappears when its last tab closes`() {
        val vm = viewModel()
        vm.visit("https://a.example/")
        vm.onEvent(BrowserUiEvent.CreateGroup(setOf(vm.selectedId()), "Solo", 0, isIncognito = false))
        vm.onEvent(BrowserUiEvent.CloseTab(vm.selectedId()))
        assertTrue(vm.state.value.groups.isEmpty())
    }

    @Test
    fun `closing a group closes exactly its tabs`() {
        val vm = viewModel()
        vm.visit("https://a.example/")
        val keep = vm.selectedId()
        vm.onEvent(BrowserUiEvent.CreateGroup(emptySet(), "Work", 1, isIncognito = false))
        val group = vm.state.value.groups.single()
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = false, groupId = group.id))
        vm.onEvent(BrowserUiEvent.CloseGroup(group.id))
        assertEquals(listOf(keep), vm.state.value.tabs.map { it.id })
    }

    @Test
    fun `opening a link in a group puts the page and the new tab in one group`() {
        val vm = viewModel()
        vm.visit("https://a.example/")
        val source = vm.selectedId()
        vm.onEvent(BrowserUiEvent.OpenLink("https://b.example/", LinkTarget.NEW_TAB_IN_GROUP))

        val state = vm.state.value
        assertEquals(source, state.selectedTabId)
        assertEquals(2, state.tabs.size)
        val groupId = state.tabs.first().groupId
        assertNotNull(groupId)
        assertTrue(state.tabs.all { it.groupId == groupId })
        assertEquals("https://b.example/", state.tabs.last().url)
        // Background tabs get no WebView until the user goes to them.
        coVerify(exactly = 0) { pool.acquire(match { it.url == "https://b.example/" }, any(), any()) }
    }

    @Test
    fun `private previews and icons stay in memory only`() {
        val vm = viewModel()
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = true))
        vm.visit("https://private.example/")
        val id = vm.selectedId()
        val picture = mockk<android.graphics.Bitmap>(relaxed = true)
        vm.onEvent(BrowserUiEvent.PreviewCaptured(id, picture))
        TabEventsBridge(vm, id).onFavicon(picture)

        assertEquals(picture, vm.state.value.previews[id])
        assertEquals(picture, vm.state.value.selectedTab?.favicon)
        coVerify(exactly = 0) { previews.save(any(), any()) }
        coVerify(exactly = 0) { favicons.save(any(), any()) }
    }

    @Test
    fun `regular previews and icons are kept for next time`() {
        val vm = viewModel()
        vm.visit("https://a.example/")
        val id = vm.selectedId()
        val picture = mockk<android.graphics.Bitmap>(relaxed = true)
        vm.onEvent(BrowserUiEvent.PreviewCaptured(id, picture))
        TabEventsBridge(vm, id).onFavicon(picture)
        coVerify { previews.save(id, picture) }
        coVerify { favicons.save("a.example", picture) }
    }

    @Test
    fun `turning previews off drops the ones in memory`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(UserSettings())
        every { settings.settings } returns flow
        val vm = viewModel()
        vm.visit("https://a.example/")
        vm.onEvent(BrowserUiEvent.PreviewCaptured(vm.selectedId(), mockk(relaxed = true)))
        flow.value = UserSettings(tabPreviews = false)
        assertTrue(vm.state.value.previews.isEmpty())
    }
}
