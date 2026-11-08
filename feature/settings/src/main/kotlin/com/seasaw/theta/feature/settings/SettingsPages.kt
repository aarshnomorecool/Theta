package com.seasaw.theta.feature.settings

import android.icu.text.NumberFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seasaw.theta.core.common.url.SearchEngine
import com.seasaw.theta.core.data.settings.AppLanguage
import com.seasaw.theta.core.data.settings.SettingFlag
import com.seasaw.theta.core.data.settings.ThemeMode
import com.seasaw.theta.core.data.settings.UserSettings
import com.seasaw.theta.core.ui.CardRow
import com.seasaw.theta.core.ui.ConfirmDialog
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.ThetaIcons

/** Pages below the settings home. The home page itself is [SettingsHome]. */
internal enum class SettingsPage(val titleRes: Int) {
    HOME(R.string.settings_title),
    SHIELDS(R.string.settings_shields),
    SEARCH_ENGINE(R.string.settings_search_engine),
    NEW_TAB(R.string.settings_new_tab_page),
    TABS(R.string.settings_tabs),
    SITE_SETTINGS(R.string.settings_site_settings),
    APPEARANCE(R.string.settings_appearance),
    ACCESSIBILITY(R.string.settings_accessibility),
    LANGUAGES(R.string.settings_language),
    CLEAR_DATA(R.string.settings_clear_browsing),
    ABOUT(R.string.settings_about),
    LICENCES(R.string.settings_licences),
    PHOTO_CREDITS(R.string.settings_photo_credits),
}

/** What pages need beyond state and events. */
internal class SettingsActions(
    val onEvent: (SettingsUiEvent) -> Unit,
    val onOpen: (SettingsPage) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onOpenAdPrivacy: () -> Unit,
    val showAdPrivacy: Boolean,
)

@Composable
internal fun SettingsPageContent(
    page: SettingsPage,
    state: SettingsUiState,
    versionName: String,
    actions: SettingsActions,
) {
    when (page) {
        SettingsPage.HOME -> Unit
        SettingsPage.SHIELDS -> ShieldsPage(state, actions)
        SettingsPage.SEARCH_ENGINE -> SearchEnginePage(state.settings, actions.onEvent)
        SettingsPage.NEW_TAB -> NewTabPage(state.settings, actions.onEvent)
        SettingsPage.TABS -> TabsPage(state.settings, actions.onEvent)
        SettingsPage.SITE_SETTINGS -> SiteSettingsPage(state, actions.onEvent)
        SettingsPage.APPEARANCE -> AppearancePage(state.settings, actions.onEvent)
        SettingsPage.ACCESSIBILITY -> AccessibilityPage(state.settings, actions.onEvent)
        SettingsPage.LANGUAGES -> LanguagesPage(state.settings, actions.onEvent)
        SettingsPage.CLEAR_DATA -> ClearDataPage(state.settings, actions.onEvent)
        SettingsPage.ABOUT -> AboutPage(state, versionName, actions)
        SettingsPage.LICENCES -> LicencesPage(actions.onOpenLink)
        SettingsPage.PHOTO_CREDITS -> PhotoCreditsPage(state, actions.onOpenLink)
    }
}

@Composable
private fun ShieldsPage(state: SettingsUiState, actions: SettingsActions) {
    val current = state.settings
    var confirmReset by remember { mutableStateOf(false) }
    ShieldsSummary(state)
    Section(null) {
        ToggleRow(
            stringResource(R.string.settings_adblock),
            current.adBlockEnabled,
            { actions.onEvent(SettingsUiEvent.SetAdBlock(it)) },
            summary = stringResource(R.string.settings_adblock_summary),
            icon = ThetaIcons.Shield,
            tag = SettingsTestTags.ADBLOCK,
        )
        ToggleRow(
            stringResource(R.string.settings_block_cookie_notices),
            current.blockCookieNotices,
            { actions.onEvent(SettingsUiEvent.Toggle(SettingFlag.BLOCK_COOKIE_NOTICES, it)) },
            summary = stringResource(R.string.settings_block_cookie_notices_summary),
            icon = ThetaIcons.Cookie,
            tag = SettingsTestTags.COOKIE_NOTICES,
        )
        ToggleRow(
            stringResource(R.string.settings_ntp_stats),
            current.ntpShowPrivacyStats,
            { actions.onEvent(SettingsUiEvent.Toggle(SettingFlag.NTP_STATS, it)) },
            summary = stringResource(R.string.settings_ntp_stats_summary),
            icon = ThetaIcons.Stats,
        )
        CardRow(
            title = stringResource(R.string.settings_reset_stats),
            summary = stringResource(R.string.settings_reset_stats_summary),
            icon = ThetaIcons.Reload,
            onClick = { confirmReset = true },
        )
    }
    Section(stringResource(R.string.settings_section_privacy)) {
        ToggleRow(
            stringResource(R.string.settings_diagnostics),
            current.diagnosticsConsent,
            { actions.onEvent(SettingsUiEvent.SetDiagnostics(it)) },
            summary = stringResource(R.string.settings_diagnostics_summary),
            icon = ThetaIcons.Stats,
        )
        if (actions.showAdPrivacy) {
            LinkRow(
                stringResource(R.string.settings_ad_privacy),
                actions.onOpenAdPrivacy,
                summary = stringResource(R.string.settings_ad_privacy_summary),
                icon = ThetaIcons.Eye,
            )
        }
        LinkRow(
            stringResource(R.string.settings_site_settings),
            { actions.onOpen(SettingsPage.SITE_SETTINGS) },
            summary = stringResource(R.string.settings_site_settings_summary),
            icon = ThetaIcons.SiteSettings,
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset_stats),
            message = stringResource(R.string.settings_reset_stats_confirm),
            confirmLabel = stringResource(R.string.settings_reset),
            onConfirm = {
                actions.onEvent(SettingsUiEvent.ResetPrivacyStats)
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** The headline number, so the page answers "is this doing anything?" before anything else. */
@Composable
private fun ShieldsSummary(state: SettingsUiState) {
    val context = LocalContext.current
    val blocked = state.stats?.blockedRequests ?: 0
    val since = state.stats?.countingSince
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.settings.adBlockEnabled) ThetaIcons.ShieldCheck else ThetaIcons.ShieldOff,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(NumberFormat.getIntegerInstance().format(blocked), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (since == null) {
                    stringResource(R.string.settings_blocked_total)
                } else {
                    stringResource(
                        R.string.settings_blocked_since,
                        DateUtils.formatDateTime(
                            context,
                            since,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
                        ),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchEnginePage(settings: UserSettings, onEvent: (SettingsUiEvent) -> Unit) {
    Section(null) {
        SearchEngine.entries.forEach { engine ->
            RadioRow(engine.displayName, engine == settings.searchEngine, {
                onEvent(SettingsUiEvent.SetSearchEngine(engine))
            })
        }
    }
    Footnote(stringResource(R.string.settings_search_engine_note))
}

@Composable
private fun NewTabPage(settings: UserSettings, onEvent: (SettingsUiEvent) -> Unit) {
    Section(null) {
        ToggleRow(
            stringResource(R.string.settings_ntp_photos),
            settings.ntpBackgroundPhotos,
            { onEvent(SettingsUiEvent.Toggle(SettingFlag.NTP_PHOTOS, it)) },
            summary = stringResource(R.string.settings_ntp_photos_summary),
            icon = ThetaIcons.Image,
        )
        ToggleRow(
            stringResource(R.string.settings_ntp_stats),
            settings.ntpShowPrivacyStats,
            { onEvent(SettingsUiEvent.Toggle(SettingFlag.NTP_STATS, it)) },
            summary = stringResource(R.string.settings_ntp_stats_summary),
            icon = ThetaIcons.Stats,
        )
        ToggleRow(
            stringResource(R.string.settings_ntp_recent),
            settings.ntpShowRecentSites,
            { onEvent(SettingsUiEvent.Toggle(SettingFlag.NTP_RECENT, it)) },
            summary = stringResource(R.string.settings_ntp_recent_summary),
            icon = ThetaIcons.Clock,
        )
    }
}

@Composable
private fun TabsPage(settings: UserSettings, onEvent: (SettingsUiEvent) -> Unit) {
    Section(null) {
        ToggleRow(
            stringResource(R.string.settings_tab_previews),
            settings.tabPreviews,
            { onEvent(SettingsUiEvent.Toggle(SettingFlag.TAB_PREVIEWS, it)) },
            summary = stringResource(R.string.settings_tab_previews_summary),
            icon = ThetaIcons.Tabs,
        )
    }
    Footnote(stringResource(R.string.settings_tabs_note))
}

@Composable
private fun SiteSettingsPage(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    Section(null) {
        ToggleRow(
            stringResource(R.string.settings_block_autoplay),
            state.settings.blockAutoplay,
            { onEvent(SettingsUiEvent.Toggle(SettingFlag.BLOCK_AUTOPLAY, it)) },
            summary = stringResource(R.string.settings_block_autoplay_summary),
            icon = ThetaIcons.Film,
        )
    }
    Section(stringResource(R.string.settings_permissions_section)) {
        if (state.siteDecisions.isEmpty()) {
            CardRow(stringResource(R.string.settings_permissions_empty))
        }
        state.siteDecisions.forEach { site ->
            CardRow(
                title = site.host,
                summary = permissionSummary(site),
                icon = ThetaIcons.Globe,
                trailing = {
                    IconButton(onClick = { onEvent(SettingsUiEvent.ForgetSite(site.host)) }) {
                        Icon(ThetaIcons.Trash, stringResource(R.string.settings_forget_site, site.host))
                    }
                },
            )
        }
    }
    Section(stringResource(R.string.settings_ad_exceptions_section)) {
        if (state.exemptSites.isEmpty()) {
            CardRow(stringResource(R.string.settings_ad_exceptions_empty))
        }
        state.exemptSites.forEach { host ->
            CardRow(
                title = host,
                icon = ThetaIcons.ShieldOff,
                trailing = {
                    IconButton(onClick = { onEvent(SettingsUiEvent.RemoveAdException(host)) }) {
                        Icon(ThetaIcons.Close, stringResource(R.string.settings_remove_exception, host))
                    }
                },
            )
        }
    }
}

@Composable
private fun permissionSummary(site: SiteDecision): String {
    val allowed = site.allowed.map { permissionLabel(it) }
    val blocked = site.blocked.map { permissionLabel(it) }
    return listOfNotNull(
        allowed.takeIf {
            it.isNotEmpty()
        }?.let { stringResource(R.string.settings_permission_allowed, it.joinToString()) },
        blocked.takeIf {
            it.isNotEmpty()
        }?.let { stringResource(R.string.settings_permission_blocked, it.joinToString()) },
    ).joinToString(" · ")
}

@Composable
private fun permissionLabel(id: String): String = when (id) {
    "camera" -> stringResource(R.string.settings_permission_camera)
    "microphone" -> stringResource(R.string.settings_permission_microphone)
    "location" -> stringResource(R.string.settings_permission_location)
    else -> id
}

@Composable
private fun AppearancePage(settings: UserSettings, onEvent: (SettingsUiEvent) -> Unit) {
    Section(stringResource(R.string.settings_theme)) {
        ThemeMode.entries.forEach { mode ->
            RadioRow(stringResource(mode.labelRes()), mode == settings.themeMode, {
                onEvent(SettingsUiEvent.SetTheme(mode))
            })
        }
    }
    Footnote(stringResource(R.string.settings_theme_note))
}

@Composable
private fun AccessibilityPage(settings: UserSettings, onEvent: (SettingsUiEvent) -> Unit) {
    Section(stringResource(R.string.settings_text_section)) {
        TextSizeCard(settings.textZoomPercent) { onEvent(SettingsUiEvent.SetTextZoom(it)) }
        ToggleRow(
            stringResource(R.string.settings_zoom_buttons),
            settings.showZoomButtons,
            { onEvent(SettingsUiEvent.Toggle(SettingFlag.ZOOM_BUTTONS, it)) },
            summary = stringResource(R.string.settings_zoom_buttons_summary),
            icon = ThetaIcons.ZoomIn,
        )
    }
    Section(stringResource(R.string.settings_display_section)) {
        ToggleRow(
            stringResource(R.string.settings_high_contrast),
            settings.highContrast,
            { onEvent(SettingsUiEvent.Toggle(SettingFlag.HIGH_CONTRAST, it)) },
            summary = stringResource(R.string.settings_high_contrast_summary),
            icon = ThetaIcons.Contrast,
            tag = SettingsTestTags.HIGH_CONTRAST,
        )
    }
    Footnote(stringResource(R.string.settings_accessibility_note))
}

/** Page text size, with a sample that grows and shrinks as the slider moves. */
@Composable
private fun TextSizeCard(percent: Int, onChange: (Int) -> Unit) {
    var value by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ThetaIcons.TextSize, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.settings_text_size),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp),
            )
            Text(
                "${value.toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toInt()) },
            valueRange = UserSettings.MIN_TEXT_ZOOM.toFloat()..UserSettings.MAX_TEXT_ZOOM.toFloat(),
            steps = (UserSettings.MAX_TEXT_ZOOM - UserSettings.MIN_TEXT_ZOOM) / TEXT_ZOOM_STEP - 1,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(SettingsTestTags.TEXT_SIZE),
        )
        Text(
            stringResource(R.string.settings_text_size_sample),
            fontSize = (SAMPLE_TEXT_SIZE * value / UserSettings.DEFAULT_TEXT_ZOOM).sp,
            lineHeight = (SAMPLE_TEXT_SIZE * value / UserSettings.DEFAULT_TEXT_ZOOM * LINE_HEIGHT).sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
                .padding(16.dp),
        )
    }
}

@Composable
private fun LanguagesPage(settings: UserSettings, onEvent: (SettingsUiEvent) -> Unit) {
    Section(null) {
        AppLanguage.entries.forEach { language ->
            RadioRow(
                stringResource(language.labelRes()),
                language == settings.language,
                { onEvent(SettingsUiEvent.SetLanguage(language)) },
            )
        }
    }
}

@Composable
private fun ClearDataPage(settings: UserSettings, onEvent: (SettingsUiEvent) -> Unit) {
    var history by rememberSaveable { mutableStateOf(true) }
    var tabs by rememberSaveable { mutableStateOf(true) }
    var siteData by rememberSaveable { mutableStateOf(true) }
    var bookmarks by rememberSaveable { mutableStateOf(false) }
    var speedDial by rememberSaveable { mutableStateOf(false) }
    var downloads by rememberSaveable { mutableStateOf(false) }
    var sitePreferences by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selection = ClearSelection(history, tabs, siteData, bookmarks, speedDial, downloads, sitePreferences)

    Section(stringResource(R.string.settings_clear_choose)) {
        CheckRow(stringResource(R.string.clear_history), history, {
            history = it
        }, stringResource(R.string.clear_history_summary))
        CheckRow(stringResource(R.string.clear_tabs), tabs, { tabs = it }, stringResource(R.string.clear_tabs_summary))
        CheckRow(stringResource(R.string.clear_site_data), siteData, {
            siteData = it
        }, stringResource(R.string.clear_site_data_summary))
        CheckRow(stringResource(R.string.clear_bookmarks), bookmarks, { bookmarks = it })
        CheckRow(stringResource(R.string.clear_speed_dial), speedDial, { speedDial = it })
        CheckRow(stringResource(R.string.clear_downloads), downloads, {
            downloads = it
        }, stringResource(R.string.clear_downloads_summary))
        CheckRow(stringResource(R.string.clear_site_preferences), sitePreferences, { sitePreferences = it })
    }
    Button(
        onClick = { confirmClear = true },
        enabled = !selection.isEmpty,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag(SettingsTestTags.CLEAR_BUTTON),
    ) { Text(stringResource(R.string.clear_button)) }
    Footnote(stringResource(R.string.clear_stats_note))
    Section(stringResource(R.string.settings_clear_automatic)) {
        ToggleRow(
            stringResource(R.string.settings_clear_on_exit),
            settings.clearOnExit,
            { onEvent(SettingsUiEvent.SetClearOnExit(it)) },
            summary = stringResource(R.string.settings_clear_on_exit_summary),
            icon = ThetaIcons.Timer,
        )
    }
    Section(stringResource(R.string.settings_clear_everything)) {
        CardRow(
            title = stringResource(R.string.settings_delete_all),
            summary = stringResource(R.string.settings_delete_all_summary),
            icon = ThetaIcons.Trash,
            onClick = { confirmDelete = true },
            modifier = Modifier.testTag(SettingsTestTags.DELETE_ALL),
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_confirm_title),
            message = stringResource(if (siteData) R.string.clear_confirm_signout else R.string.clear_confirm_message),
            confirmLabel = stringResource(R.string.clear_button),
            onConfirm = {
                onEvent(SettingsUiEvent.Clear(selection))
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_all),
            message = stringResource(R.string.settings_delete_all_confirm),
            confirmLabel = stringResource(CoreUiR.string.action_delete),
            onConfirm = {
                onEvent(SettingsUiEvent.DeleteAllData)
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun AboutPage(state: SettingsUiState, versionName: String, actions: SettingsActions) {
    Section(null) {
        CardRow(stringResource(R.string.settings_version), summary = versionName, icon = ThetaIcons.Info)
        if (state.privacyPolicyUrl.isNotEmpty()) {
            LinkRow(stringResource(R.string.settings_privacy_policy), {
                actions.onOpenLink(state.privacyPolicyUrl)
            }, icon = ThetaIcons.Lock)
        }
        LinkRow(
            stringResource(R.string.settings_licences),
            { actions.onOpen(SettingsPage.LICENCES) },
            summary = stringResource(R.string.settings_licences_summary),
            icon = ThetaIcons.Licence,
        )
        LinkRow(
            stringResource(R.string.settings_photo_credits),
            { actions.onOpen(SettingsPage.PHOTO_CREDITS) },
            summary = stringResource(R.string.settings_photo_credits_summary),
            icon = ThetaIcons.Camera,
        )
    }
}

/** [noticeRes] is the full licence text, for licences that ask for their notice to ship with the app. */
private class Licence(val nameRes: Int, val termsRes: Int, val url: String, val noticeRes: Int? = null)

private val licences = listOf(
    Licence(R.string.licence_filters, R.string.licence_filters_terms, "https://easylist.to/pages/licence.html"),
    Licence(R.string.licence_cookies, R.string.licence_cookies_terms, "https://easylist.to/"),
    Licence(R.string.licence_icons, R.string.licence_icons_terms, "https://lucide.dev/license", R.raw.lucide_licence),
    Licence(R.string.licence_android, R.string.licence_apache, "https://www.apache.org/licenses/LICENSE-2.0"),
    Licence(R.string.licence_kotlin, R.string.licence_apache, "https://www.apache.org/licenses/LICENSE-2.0"),
    Licence(R.string.licence_okhttp, R.string.licence_apache, "https://www.apache.org/licenses/LICENSE-2.0"),
)

@Composable
private fun LicencesPage(onOpenLink: (String) -> Unit) {
    var notice by remember { mutableStateOf<Licence?>(null) }
    Section(null) {
        licences.forEach { licence ->
            LinkRow(
                stringResource(licence.nameRes),
                { if (licence.noticeRes != null) notice = licence else onOpenLink(licence.url) },
                summary = stringResource(licence.termsRes),
            )
        }
    }
    notice?.let { licence -> LicenceNoticeDialog(licence, onDismiss = { notice = null }) }
}

@Composable
private fun LicenceNoticeDialog(licence: Licence, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember(licence) {
        licence.noticeRes?.let { res ->
            context.resources.openRawResource(res).bufferedReader().use { it.readText() }
        }.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(licence.nameRes)) },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.action_close)) } },
    )
}

@Composable
private fun PhotoCreditsPage(state: SettingsUiState, onOpenLink: (String) -> Unit) {
    Section(null) {
        state.photoCredits.forEach { photo ->
            LinkRow(
                photo.title,
                { onOpenLink(photo.sourceUrl) },
                summary = stringResource(R.string.settings_photo_by, photo.author, photo.licence),
            )
        }
    }
    Footnote(stringResource(R.string.settings_photo_credits_note))
}

internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

internal fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.HINDI -> R.string.language_hindi
    AppLanguage.MARATHI -> R.string.language_marathi
    AppLanguage.RUSSIAN -> R.string.language_russian
}

private const val TEXT_ZOOM_STEP = 10
private const val SAMPLE_TEXT_SIZE = 16f
private const val LINE_HEIGHT = 1.4f
