package com.seasaw.theta.feature.browser

import android.webkit.ValueCallback
import android.webkit.WebView
import com.seasaw.theta.core.data.AdBlockExemptionsRepository
import com.seasaw.theta.core.data.EraseRequest
import com.seasaw.theta.core.data.LocalDataEraser
import com.seasaw.theta.core.data.TabsRepository
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.data.settings.UserSettings
import com.seasaw.theta.core.webview.FileChooserRequest
import com.seasaw.theta.core.webview.PageError
import com.seasaw.theta.core.webview.PermissionPrompt
import com.seasaw.theta.core.webview.SitePermission
import com.seasaw.theta.core.webview.WebViewPool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Regression tests for the security review: prompts, crash loops, startup wipes and UI navigation. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelSafetyTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val pool = mockk<WebViewPool>(relaxed = true)
    private val tabs = mockk<TabsRepository>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val eraser = mockk<LocalDataEraser>(relaxed = true)
    private val erasures = MutableSharedFlow<EraseRequest>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settings.settings } returns flowOf(UserSettings())
        every { eraser.erasures } returns erasures
        coEvery { tabs.restore() } returns TabsRepository.Saved(emptyList(), emptyList())
        coEvery { pool.acquire(any(), any(), any()) } returns mockk<WebView>(relaxed = true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        exemptions: AdBlockExemptionsRepository = mockk { every { observeHosts() } returns flowOf(emptyList()) },
    ) = BrowserViewModel(
        pool = pool,
        webViewFactory = mockk(relaxed = true),
        tabsRepository = tabs,
        history = mockk(relaxed = true),
        bookmarks = mockk { every { observeIsBookmarked(any()) } returns flowOf(false) },
        speedDial = mockk(relaxed = true),
        downloads = mockk(relaxed = true),
        exemptions = exemptions,
        settings = settings,
        eraser = eraser,
        webDataCleaner = mockk(relaxed = true),
        incognito = mockk(relaxed = true),
        downloadEnqueuer = mockk(relaxed = true),
        externalLinks = mockk(relaxed = true),
        analytics = mockk(relaxed = true),
        previewStore = mockk(relaxed = true),
        faviconStore = mockk(relaxed = true),
        appScope = CoroutineScope(dispatcher),
        mainDispatcher = dispatcher,
    )

    private fun BrowserViewModel.selectedId() = checkNotNull(state.value.selectedTabId)

    private fun BrowserViewModel.visit(url: String) =
        TabEventsBridge(this, selectedId()).onNavigated(url, canGoBack = false, canGoForward = false)

    /** Regression test: a site's question stayed up over the next tab, as if that site were asking. */
    @Test
    fun `switching tabs withdraws the old tab's question and denies it`() {
        val vm = viewModel()
        vm.visit("https://a.example/")
        val first = vm.selectedId()
        var answer: Boolean? = null
        TabEventsBridge(vm, first).onPermissionPrompt(
            PermissionPrompt("a.example", setOf(SitePermission.CAMERA)) { granted, _ -> answer = granted },
        )
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = false))
        assertNull(vm.state.value.prompt)
        assertEquals(false, answer)
    }

    @Test
    fun `selecting the tab already on screen keeps its question`() {
        val vm = viewModel()
        val id = vm.selectedId()
        val prompt = PermissionPrompt("a.example", setOf(SitePermission.CAMERA)) { _, _ -> }
        TabEventsBridge(vm, id).onPermissionPrompt(prompt)
        vm.onEvent(BrowserUiEvent.SelectTab(id))
        assertTrue(vm.state.value.prompt is BrowserPrompt.Permission)
    }

    /** Regression test: a page that crashed its renderer on every load was reloaded forever. */
    @Test
    fun `a page that crashes again right after reloading is not reloaded again`() {
        val vm = viewModel()
        vm.visit("https://crash.example/")
        val id = vm.selectedId()
        val bridge = TabEventsBridge(vm, id)
        bridge.onRendererGone()
        assertNull(vm.state.value.selectedTab?.error)
        bridge.onRendererGone()
        assertTrue(vm.state.value.selectedTab?.error is PageError.Crashed)
    }

    /** WebViews share one renderer, so its crash reaches background tabs whose own pages were fine. */
    @Test
    fun `a renderer crash does not mark background tabs as crashing`() {
        val vm = viewModel()
        vm.visit("https://background.example/")
        val background = vm.selectedId()
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = false))
        val bridge = TabEventsBridge(vm, background)
        bridge.onRendererGone()
        bridge.onRendererGone()
        assertNull(vm.tab(background)?.error)
    }

    /** Regression test: with clear-on-exit, the startup wipe closed a link that another app had just opened. */
    @Test
    fun `the clear-on-exit wipe keeps a link opened from another app`() {
        every { settings.settings } returns flowOf(UserSettings(clearOnExit = true))
        val wipeMayFinish = CompletableDeferred<Unit>()
        coEvery { eraser.erase(any()) } coAnswers {
            wipeMayFinish.await()
            erasures.emit(firstArg())
        }
        val vm = viewModel()
        vm.openFromIntent("https://link.example/")
        wipeMayFinish.complete(Unit)
        assertTrue(vm.state.value.tabs.any { it.url == "https://link.example/" })
    }

    @Test
    fun `the ui cannot load script or local file addresses`() {
        val vm = viewModel()
        vm.onEvent(BrowserUiEvent.OpenUrl("javascript:alert(document.cookie)"))
        vm.onEvent(BrowserUiEvent.OpenUrl("file:///sdcard/Download/x.html"))
        vm.openFromIntent("content://evil/x")
        assertTrue(vm.state.value.tabs.all { it.url.isEmpty() })
        coVerify(exactly = 0) { pool.acquire(any(), any(), any()) }
    }

    /** Regression test: "allow ads on this site" in a private tab wrote the site to the database. */
    @Test
    fun `private tabs cannot record an ad blocking exemption`() {
        val exemptions = mockk<AdBlockExemptionsRepository>(relaxed = true)
        every { exemptions.observeHosts() } returns flowOf(emptyList())
        val vm = viewModel(exemptions)
        vm.onEvent(BrowserUiEvent.NewTab(isIncognito = true))
        vm.visit("https://private.example/")
        vm.onEvent(BrowserUiEvent.ToggleSiteAdBlock)
        coVerify(exactly = 0) { exemptions.exempt(any()) }
    }

    /** Regression test: a second file request replaced the first, which then never got an answer. */
    @Test
    fun `a file request that cannot be shown is answered straight away`() {
        val vm = viewModel()
        val id = vm.selectedId()
        val bridge = TabEventsBridge(vm, id)
        bridge.onFileChooser(FileChooserRequest(mockk(relaxed = true)) { })
        var secondAnswered = false
        bridge.onFileChooser(FileChooserRequest(mockk(relaxed = true), ValueCallback { secondAnswered = true }))
        assertTrue(secondAnswered)
    }
}
