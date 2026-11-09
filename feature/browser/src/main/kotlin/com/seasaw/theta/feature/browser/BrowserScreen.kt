package com.seasaw.theta.feature.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme
import com.seasaw.theta.core.webview.PageError

/**
 * The route the app navigates to.
 *
 * @param newTabContent the new-tab page. It belongs to another feature, and features cannot depend on each other, so
 * the app passes it in.
 */
@Composable
fun BrowserRoute(
    isDarkTheme: Boolean,
    navigation: BrowserNavigation,
    incomingUrl: String?,
    onIncomingUrlHandled: () -> Unit,
    newTabContent: @Composable (onOpenUrl: (String) -> Unit) -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(isDarkTheme) { viewModel.onEvent(BrowserUiEvent.ThemeChanged(isDarkTheme)) }
    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) {
            viewModel.openFromIntent(incomingUrl)
            onIncomingUrlHandled()
        }
    }
    DisposableEffect(activity) {
        viewModel.attachHost(activity)
        onDispose { viewModel.attachHost(null) }
    }

    // The switcher shows pictures of private pages too, even while the selected tab is a regular one.
    val isIncognitoVisible = state.selectedTab?.isIncognito == true ||
        (state.isTabSwitcherOpen && state.tabs.any { it.isIncognito })
    DisposableEffect(activity, isIncognitoVisible) {
        // Keeps private pages out of screenshots and the recent apps preview.
        if (isIncognitoVisible) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    BrowserScreen(
        state = state,
        navigation = navigation,
        onEvent = viewModel::onEvent,
        webViewFor = viewModel::webView,
        onOpenExternal = { prompt -> activity?.let { viewModel.launchExternal(prompt.link, it) } },
        newTabContent = newTabContent,
    )
}

@Composable
internal fun BrowserScreen(
    state: BrowserUiState,
    navigation: BrowserNavigation,
    onEvent: (BrowserUiEvent) -> Unit,
    webViewFor: (String) -> android.webkit.WebView?,
    onOpenExternal: (BrowserPrompt.OpenExternally) -> Unit,
    newTabContent: @Composable (onOpenUrl: (String) -> Unit) -> Unit,
) {
    val tab = state.selectedTab
    var isMenuOpen by remember { mutableStateOf(false) }
    var isTextSizeOpen by remember { mutableStateOf(false) }
    var groupingTabId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }

    /** Pictures the page for the tab switcher before it leaves the screen, then carries on. */
    fun withPreview(then: () -> Unit) {
        val current = state.selectedTab
        if (!current.showsWebPage() || !state.isTabPreviewsEnabled) {
            then()
            return
        }
        PreviewCapture.capture(webViewFor(current!!.id), window) { preview ->
            preview?.let { onEvent(BrowserUiEvent.PreviewCaptured(current.id, it)) }
            then()
        }
    }
    val snackbars = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.textRes()) }

    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbars.showSnackbar(messageText)
            onEvent(BrowserUiEvent.MessageShown)
        }
    }

    state.fullscreen?.let { fullscreen ->
        FullscreenVideo(fullscreen) { onEvent(BrowserUiEvent.ExitFullscreen) }
        return
    }

    BackHandler(enabled = state.isTabSwitcherOpen || state.find != null || tab?.isNewTab == false) {
        when {
            state.isTabSwitcherOpen -> onEvent(BrowserUiEvent.ShowTabSwitcher(false))
            state.find != null -> onEvent(BrowserUiEvent.CloseFind)
            else -> onEvent(BrowserUiEvent.GoBack)
        }
    }

    state.fileChooser?.let { request ->
        FileChooserEffect(request.params, request.callback) { onEvent(BrowserUiEvent.FileChooserHandled) }
    }

    if (state.isTabSwitcherOpen) {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .systemBarsPadding(),
        ) {
            TabSwitcher(
                state = state,
                onEvent = onEvent,
                navigation = SwitcherNavigation(
                    onDismiss = { onEvent(BrowserUiEvent.ShowTabSwitcher(false)) },
                    onSettings = navigation.onSettings,
                    onClearBrowsingData = navigation.onClearBrowsingData,
                ),
            )
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            AddressBar(
                url = tab?.url.orEmpty(),
                isIncognito = tab?.isIncognito == true,
                isLoading = tab?.isLoading == true,
                onSubmit = { onEvent(BrowserUiEvent.Submit(it)) },
                onReload = { onEvent(BrowserUiEvent.Reload) },
                onStop = { onEvent(BrowserUiEvent.StopLoading) },
                isCertificateError = tab?.error is PageError.Ssl,
            )
            if (tab?.isLoading == true) PageProgress(tab.progress)
            state.find?.let { find ->
                FindBar(
                    find = find,
                    onQuery = { onEvent(BrowserUiEvent.FindQuery(it)) },
                    onStep = { onEvent(BrowserUiEvent.FindStep(it)) },
                    onClose = { onEvent(BrowserUiEvent.CloseFind) },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    tab == null -> Unit

                    tab.isNewTab && tab.isIncognito -> IncognitoNewTab(state.isIncognitoIsolationSupported)

                    tab.isNewTab -> newTabContent { onEvent(BrowserUiEvent.OpenUrl(it)) }

                    tab.error != null -> PageErrorView(
                        isSslError = tab.error is PageError.Ssl,
                        onRetry = { onEvent(BrowserUiEvent.Reload) },
                        modifier = Modifier.fillMaxSize(),
                        isCrash = tab.error is PageError.Crashed,
                    )

                    else -> WebViewHost(tab.id, state.webViewGeneration, webViewFor)
                }
                if (state.showZoomButtons && tab.showsWebPage()) {
                    ZoomButtons(
                        percent = state.textZoomPercent,
                        onChange = { onEvent(BrowserUiEvent.SetTextZoom(it)) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    )
                }
            }
            BrowserToolbar(
                canGoBack = tab?.isNewTab == false,
                canGoForward = tab?.canGoForward == true,
                tabCount = state.tabs.count { it.isIncognito == (tab?.isIncognito == true) },
                onBack = { onEvent(BrowserUiEvent.GoBack) },
                onForward = { onEvent(BrowserUiEvent.GoForward) },
                onNewTab = { withPreview { onEvent(BrowserUiEvent.NewTab(tab?.isIncognito == true, tab?.groupId)) } },
                onShowTabs = { withPreview { onEvent(BrowserUiEvent.ShowTabSwitcher(true)) } },
                onMenu = { isMenuOpen = true },
            )
            Box(Modifier.align(Alignment.End)) {
                BrowserMenu(
                    expanded = isMenuOpen,
                    state = state,
                    navigation = navigation,
                    onEvent = onEvent,
                    onShare = { tab?.let { context.shareUrl(it.url, it.title) } },
                    onTextSize = { isTextSizeOpen = true },
                    onDismiss = { isMenuOpen = false },
                    onAddToGroup = { groupingTabId = tab?.id },
                )
            }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
    }

    if (isTextSizeOpen) {
        TextSizeDialog(state.textZoomPercent, { onEvent(BrowserUiEvent.SetTextZoom(it)) }, { isTextSizeOpen = false })
    }
    PromptHost(state.prompt, onEvent, onOpenExternal)
    state.linkMenuUrl?.let { url -> LinkMenu(url, fromPrivateTab = tab?.isIncognito == true, onEvent = onEvent) }
    groupingTabId?.let { id ->
        GroupTabsFlow(setOf(id), isIncognito = tab?.isIncognito == true, state = state, onEvent = onEvent) {
            groupingTabId = null
        }
    }
}

/** Accessibility: bigger and smaller page text without a pinch, for anyone who finds pinching hard. */
@Composable
private fun ZoomButtons(percent: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ZoomButton("A+", stringResource(R.string.zoom_in)) { onChange(percent + ZOOM_STEP) }
        ZoomButton("A\u2212", stringResource(R.string.zoom_out)) { onChange(percent - ZOOM_STEP) }
    }
}

@Composable
private fun ZoomButton(text: String, description: String, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.semantics { contentDescription = description },
    ) { Text(text, style = MaterialTheme.typography.titleSmall) }
}

private const val ZOOM_STEP = 10

/** A page from the web is on screen, rather than the new-tab page or an error. */
private fun BrowserTab?.showsWebPage(): Boolean = this != null && !isNewTab && error == null

/** Hosts the pool's WebView. The pool owns its lifetime: leaving the composition only detaches the view. */
@Composable
private fun WebViewHost(tabId: String, generation: Int, webViewFor: (String) -> android.webkit.WebView?) {
    val currentWebViewFor by rememberUpdatedState(webViewFor)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { FrameLayout(it) },
        update = { container ->
            val webView = currentWebViewFor(tabId)
            val alreadyShown = container.childCount == 1 && container.getChildAt(0) === webView
            if (!alreadyShown) {
                container.removeAllViews()
                if (webView != null) {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    container.addView(webView, FrameLayout.LayoutParams(-1, -1))
                }
            }
            container.tag = generation
        },
        onRelease = { container -> container.removeAllViews() },
    )
}

@Composable
private fun IncognitoNewTab(isIsolationSupported: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLowest)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 32.dp, bottom = 20.dp)
                .size(80.dp)
                .background(colors.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ThetaIcons.Private, null, tint = colors.onSurface, modifier = Modifier.size(40.dp))
        }
        Text(stringResource(R.string.incognito_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.incognito_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 16.dp)
                .background(colors.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(20.dp),
        )
        if (!isIsolationSupported) {
            Text(
                stringResource(R.string.incognito_no_isolation),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onErrorContainer,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .background(colors.errorContainer, MaterialTheme.shapes.large)
                    .padding(20.dp),
            )
        }
    }
}

@Composable
private fun FullscreenVideo(content: FullscreenContent, onExit: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)
    DisposableEffect(content) {
        val window = context.findActivity()?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { insets?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                (content.view.parent as? ViewGroup)?.removeView(content.view)
                addView(content.view, FrameLayout.LayoutParams(-1, -1))
            }
        },
    )
}

@Composable
private fun FileChooserEffect(
    params: WebChromeClient.FileChooserParams,
    callback: android.webkit.ValueCallback<Array<Uri>>,
    onHandled: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        onHandled()
    }
    LaunchedEffect(params) {
        // A page's upload field must always get an answer, or it stays stuck waiting.
        runCatching { launcher.launch(params.createIntent()) }.onFailure {
            callback.onReceiveValue(null)
            onHandled()
        }
    }
}

private fun BrowserMessage.textRes(): Int = when (this) {
    BrowserMessage.DOWNLOAD_STARTED -> R.string.message_download_started
    BrowserMessage.DOWNLOAD_FAILED -> R.string.message_download_failed
    BrowserMessage.DOWNLOAD_UNSUPPORTED -> R.string.message_download_unsupported
    BrowserMessage.DOWNLOAD_NEEDS_STORAGE -> R.string.message_download_storage
    BrowserMessage.NO_APP_FOUND -> R.string.message_no_app
    BrowserMessage.PAGE_RELOADED -> R.string.message_page_reloaded
    BrowserMessage.OPENED_IN_BACKGROUND -> R.string.message_opened_background
    BrowserMessage.LINK_COPIED -> R.string.message_link_copied
}

private fun Context.shareUrl(url: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }
    startActivity(Intent.createChooser(send, null))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@ThemePreviews
@Composable
private fun IncognitoPreview() {
    ThetaTheme(darkTheme = true) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { IncognitoNewTab(isIsolationSupported = false) }
    }
}

@ThemePreviews
@Composable
private fun BrowserPreview() {
    ThetaTheme {
        BrowserScreen(
            state = BrowserUiState(tabs = listOf(BrowserTab("1")), selectedTabId = "1"),
            navigation = BrowserNavigation(),
            onEvent = {},
            webViewFor = { null },
            onOpenExternal = {},
            newTabContent = { Text("New tab page") },
        )
    }
}
