package com.seasaw.theta.feature.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasaw.theta.core.ui.AppLocales
import com.seasaw.theta.core.ui.CardRow
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme
import com.seasaw.theta.core.ui.ThetaTopBar
import java.text.NumberFormat

internal object SettingsTestTags {
    const val DELETE_ALL = "settings_delete_all"
    const val ADBLOCK = "settings_adblock"
    const val LITE = "settings_lite"
    const val HIGH_CONTRAST = "settings_high_contrast"
    const val TEXT_SIZE = "settings_text_size"
    const val CLEAR_BUTTON = "settings_clear_button"
    const val SEARCH = "settings_search"
}

/** What Settings needs from outside this feature. The app supplies it. */
data class AdPrivacyOptions(val isRequired: Boolean = false, val show: (Activity) -> Unit = {})

/**
 * @param onOpenLink opens a page in a Theta tab, so links from settings never leave the app
 * @param startOnClearData opens straight on the clear browsing data page, for the tab switcher's shortcut
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    versionName: String,
    adPrivacy: AdPrivacyOptions,
    onOpenLink: (String) -> Unit,
    startOnClearData: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var roleAnswers by remember { mutableIntStateOf(0) }
    val roleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { roleAnswers++ }
    val isDefaultBrowser = remember(roleAnswers) { context.isDefaultBrowser() }
    SettingsScreen(
        state = state,
        versionName = versionName,
        showAdPrivacy = adPrivacy.isRequired,
        startPage = if (startOnClearData) SettingsPage.CLEAR_DATA else SettingsPage.HOME,
        isDefaultBrowser = isDefaultBrowser,
        onBack = onBack,
        onEvent = { event ->
            viewModel.onEvent(event)
            if (event is SettingsUiEvent.SetLanguage) {
                context.findActivity()?.let { AppLocales.apply(it, event.language.languageTag) }
            }
        },
        onDefaultBrowser = {
            viewModel.onEvent(SettingsUiEvent.DefaultBrowserPromptShown)
            context.defaultBrowserIntent()?.let(roleLauncher::launch)
        },
        onOpenAdPrivacy = { context.findActivity()?.let(adPrivacy.show) },
        onOpenLink = onOpenLink,
    )
}

private val pageStackSaver = listSaver<SnapshotStateList<SettingsPage>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names -> names.map(SettingsPage::valueOf).toMutableStateList() },
)

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    versionName: String,
    showAdPrivacy: Boolean,
    onBack: () -> Unit,
    onEvent: (SettingsUiEvent) -> Unit,
    onDefaultBrowser: () -> Unit,
    onOpenAdPrivacy: () -> Unit,
    onOpenLink: (String) -> Unit,
    startPage: SettingsPage = SettingsPage.HOME,
    isDefaultBrowser: Boolean = false,
) {
    val stack = rememberSaveable(saver = pageStackSaver) { mutableListOf(startPage).toMutableStateList() }
    val page = stack.last()
    val snackbars = remember { SnackbarHostState() }
    val messageText = state.message?.let {
        stringResource(
            if (it ==
                SettingsMessage.STATS_RESET
            ) {
                R.string.settings_stats_reset
            } else {
                R.string.settings_data_cleared
            },
        )
    }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbars.showSnackbar(messageText)
            onEvent(SettingsUiEvent.MessageShown)
        }
    }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val actions = SettingsActions(
        onEvent = onEvent,
        onOpen = { stack.add(it) },
        onOpenLink = onOpenLink,
        onOpenAdPrivacy = onOpenAdPrivacy,
        showAdPrivacy = showAdPrivacy,
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Box(Modifier.systemBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                ThetaTopBar(stringResource(page.titleRes), onBack = {
                    if (stack.size >
                        1
                    ) {
                        stack.removeAt(stack.lastIndex)
                    } else {
                        onBack()
                    }
                })
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (page == SettingsPage.HOME) {
                        SettingsHome(state, versionName, isDefaultBrowser, onDefaultBrowser, actions)
                    } else {
                        SettingsPageContent(page, state, versionName, actions)
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** One searchable thing, and the page it lives on. */
private class SearchEntry(val titleRes: Int, val page: SettingsPage)

private val searchEntries = listOf(
    SearchEntry(R.string.settings_shields, SettingsPage.SHIELDS),
    SearchEntry(R.string.settings_adblock, SettingsPage.SHIELDS),
    SearchEntry(R.string.settings_reset_stats, SettingsPage.SHIELDS),
    SearchEntry(R.string.settings_diagnostics, SettingsPage.SHIELDS),
    SearchEntry(R.string.settings_search_engine, SettingsPage.SEARCH_ENGINE),
    SearchEntry(R.string.settings_new_tab_page, SettingsPage.NEW_TAB),
    SearchEntry(R.string.settings_ntp_photos, SettingsPage.NEW_TAB),
    SearchEntry(R.string.settings_ntp_stats, SettingsPage.NEW_TAB),
    SearchEntry(R.string.settings_ntp_recent, SettingsPage.NEW_TAB),
    SearchEntry(R.string.settings_tabs, SettingsPage.TABS),
    SearchEntry(R.string.settings_tab_previews, SettingsPage.TABS),
    SearchEntry(R.string.settings_site_settings, SettingsPage.SITE_SETTINGS),
    SearchEntry(R.string.settings_block_autoplay, SettingsPage.SITE_SETTINGS),
    SearchEntry(R.string.settings_permissions_section, SettingsPage.SITE_SETTINGS),
    SearchEntry(R.string.settings_ad_exceptions_section, SettingsPage.SITE_SETTINGS),
    SearchEntry(R.string.settings_appearance, SettingsPage.APPEARANCE),
    SearchEntry(R.string.settings_theme, SettingsPage.APPEARANCE),
    SearchEntry(R.string.settings_accessibility, SettingsPage.ACCESSIBILITY),
    SearchEntry(R.string.settings_text_size, SettingsPage.ACCESSIBILITY),
    SearchEntry(R.string.settings_zoom_buttons, SettingsPage.ACCESSIBILITY),
    SearchEntry(R.string.settings_high_contrast, SettingsPage.ACCESSIBILITY),
    SearchEntry(R.string.settings_language, SettingsPage.LANGUAGES),
    SearchEntry(R.string.settings_clear_browsing, SettingsPage.CLEAR_DATA),
    SearchEntry(R.string.settings_clear_on_exit, SettingsPage.CLEAR_DATA),
    SearchEntry(R.string.settings_delete_all, SettingsPage.CLEAR_DATA),
    SearchEntry(R.string.settings_about, SettingsPage.ABOUT),
    SearchEntry(R.string.settings_licences, SettingsPage.LICENCES),
    SearchEntry(R.string.settings_photo_credits, SettingsPage.PHOTO_CREDITS),
)

@Composable
private fun SettingsHome(
    state: SettingsUiState,
    versionName: String,
    isDefaultBrowser: Boolean,
    onDefaultBrowser: () -> Unit,
    actions: SettingsActions,
) {
    var query by rememberSaveable { mutableStateOf("") }
    SearchField(query, onQuery = { query = it })
    if (query.isNotBlank()) {
        val titles = searchEntries.map { stringResource(it.titleRes) }
        val pageTitles = searchEntries.map { stringResource(it.page.titleRes) }
        val hits = searchEntries.indices.filter { titles[it].contains(query.trim(), ignoreCase = true) }
        Section(null) {
            if (hits.isEmpty()) CardRow(stringResource(R.string.settings_search_none))
            hits.forEach { i ->
                LinkRow(
                    titles[i],
                    { actions.onOpen(searchEntries[i].page) },
                    summary = pageTitles[i].takeIf {
                        it !=
                            titles[i]
                    },
                )
            }
        }
        return
    }

    if (!isDefaultBrowser) {
        Section(null) {
            LinkRow(
                stringResource(R.string.settings_default_browser),
                onDefaultBrowser,
                summary = stringResource(R.string.settings_default_browser_summary),
                icon = ThetaIcons.Globe,
            )
        }
    }
    val current = state.settings
    Section(stringResource(R.string.settings_section_features)) {
        LinkRow(
            stringResource(R.string.settings_shields),
            { actions.onOpen(SettingsPage.SHIELDS) },
            summary = shieldsSummary(state),
            icon = ThetaIcons.Shield,
        )
        ToggleRow(
            stringResource(R.string.settings_lite),
            current.liteMode,
            { actions.onEvent(SettingsUiEvent.SetLiteMode(it)) },
            summary = stringResource(R.string.settings_lite_summary),
            icon = ThetaIcons.Lite,
            tag = SettingsTestTags.LITE,
        )
        LinkRow(
            stringResource(R.string.settings_new_tab_page),
            { actions.onOpen(SettingsPage.NEW_TAB) },
            summary = stringResource(R.string.settings_new_tab_page_summary),
            icon = ThetaIcons.Home,
        )
    }
    Section(stringResource(R.string.settings_section_general)) {
        LinkRow(
            stringResource(R.string.settings_search_engine),
            { actions.onOpen(SettingsPage.SEARCH_ENGINE) },
            summary = current.searchEngine.displayName,
            icon = ThetaIcons.Search,
        )
        LinkRow(
            stringResource(R.string.settings_tabs),
            { actions.onOpen(SettingsPage.TABS) },
            summary = stringResource(R.string.settings_tabs_summary),
            icon = ThetaIcons.Tabs,
        )
        LinkRow(
            stringResource(R.string.settings_site_settings),
            { actions.onOpen(SettingsPage.SITE_SETTINGS) },
            summary = stringResource(R.string.settings_site_settings_summary),
            icon = ThetaIcons.SiteSettings,
        )
        LinkRow(
            stringResource(R.string.settings_clear_browsing),
            { actions.onOpen(SettingsPage.CLEAR_DATA) },
            summary = stringResource(R.string.settings_clear_browsing_summary),
            icon = ThetaIcons.Trash,
        )
    }
    Section(stringResource(R.string.settings_section_display)) {
        LinkRow(
            stringResource(R.string.settings_appearance),
            { actions.onOpen(SettingsPage.APPEARANCE) },
            summary = stringResource(current.themeMode.labelRes()),
            icon = ThetaIcons.Palette,
        )
        LinkRow(
            stringResource(R.string.settings_accessibility),
            { actions.onOpen(SettingsPage.ACCESSIBILITY) },
            summary = stringResource(R.string.settings_accessibility_summary),
            icon = ThetaIcons.Accessibility,
        )
        LinkRow(
            stringResource(R.string.settings_language),
            { actions.onOpen(SettingsPage.LANGUAGES) },
            summary = stringResource(current.language.labelRes()),
            icon = ThetaIcons.Languages,
        )
    }
    Section(stringResource(R.string.settings_section_about)) {
        LinkRow(
            stringResource(R.string.settings_about),
            { actions.onOpen(SettingsPage.ABOUT) },
            summary = stringResource(R.string.settings_about_summary, versionName),
            icon = ThetaIcons.Info,
        )
    }
}

@Composable
private fun shieldsSummary(state: SettingsUiState): String {
    if (!state.settings.adBlockEnabled) return stringResource(R.string.settings_shields_off)
    val blocked = state.stats?.blockedRequests ?: 0
    return stringResource(R.string.settings_shields_on, NumberFormat.getIntegerInstance().format(blocked))
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colors.surfaceContainerHigh, CircleShape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ThetaIcons.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .testTag(SettingsTestTags.SEARCH),
            decorationBox = { field ->
                if (query.isEmpty()) {
                    Text(stringResource(R.string.settings_search_hint), color = colors.onSurfaceVariant)
                }
                field()
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }) {
                Icon(ThetaIcons.Close, stringResource(CoreUiR.string.action_clear_all), Modifier.size(18.dp))
            }
        }
    }
}

/** The role API is the proper prompt on Android 10 and up. Older versions can only send people to system settings. */
private fun Context.defaultBrowserIntent(): Intent? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
            return roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
        }
        return null
    }
    return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}

/** Below Android 10 there is no way to ask, so the row always shows there. */
private fun Context.isDefaultBrowser(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
    getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_BROWSER)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@ThemePreviews
@Composable
private fun SettingsPreview() {
    ThetaTheme {
        SettingsScreen(SettingsUiState(), "0.2.0", true, {}, {}, {}, {}, {})
    }
}
