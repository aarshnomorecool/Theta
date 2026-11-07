package com.seasaw.theta.feature.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasaw.theta.core.common.url.SearchEngine
import com.seasaw.theta.core.data.settings.AppLanguage
import com.seasaw.theta.core.data.settings.ThemeMode
import com.seasaw.theta.core.ui.AppLocales
import com.seasaw.theta.core.ui.ConfirmDialog
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaTheme
import com.seasaw.theta.core.ui.ThetaTopBar

internal object SettingsTestTags {
    const val DELETE_ALL = "settings_delete_all"
    const val ADBLOCK = "settings_adblock"
    const val LITE = "settings_lite"
}

/** What Settings needs from outside this feature. The app supplies it. */
data class AdPrivacyOptions(val isRequired: Boolean = false, val show: (Activity) -> Unit = {})

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    versionName: String,
    adPrivacy: AdPrivacyOptions,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    SettingsScreen(
        state = state,
        versionName = versionName,
        showAdPrivacy = adPrivacy.isRequired,
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
        onOpenLink = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
    )
}

private enum class PickerKind { THEME, LANGUAGE, SEARCH }

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
) {
    val current = state.settings
    var picker by remember { mutableStateOf<PickerKind?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbars = remember { SnackbarHostState() }
    val clearedText = stringResource(R.string.settings_data_cleared)

    LaunchedEffect(state.justCleared) {
        if (state.justCleared) {
            snackbars.showSnackbar(clearedText)
            onEvent(SettingsUiEvent.ClearedMessageShown)
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding()) {
            ThetaTopBar(stringResource(R.string.settings_title), onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionTitle(R.string.settings_section_general)
                ValueRow(R.string.settings_search_engine, current.searchEngine.displayName) {
                    picker = PickerKind.SEARCH
                }
                ValueRow(R.string.settings_theme, stringResource(current.themeMode.labelRes())) {
                    picker =
                        PickerKind.THEME
                }
                ValueRow(R.string.settings_language, stringResource(current.language.labelRes())) {
                    picker =
                        PickerKind.LANGUAGE
                }
                ActionRow(
                    R.string.settings_default_browser,
                    R.string.settings_default_browser_summary,
                    onClick = onDefaultBrowser,
                )

                SectionTitle(R.string.settings_section_browsing)
                ToggleRow(
                    R.string.settings_adblock,
                    R.string.settings_adblock_summary,
                    current.adBlockEnabled,
                    SettingsTestTags.ADBLOCK,
                ) {
                    onEvent(SettingsUiEvent.SetAdBlock(it))
                }
                ToggleRow(
                    R.string.settings_lite,
                    R.string.settings_lite_summary,
                    current.liteMode,
                    SettingsTestTags.LITE,
                ) {
                    onEvent(SettingsUiEvent.SetLiteMode(it))
                }

                SectionTitle(R.string.settings_section_privacy)
                ActionRow(R.string.settings_clear_browsing, R.string.settings_clear_browsing_summary) {
                    confirmClear =
                        true
                }
                ToggleRow(
                    R.string.settings_clear_on_exit,
                    R.string.settings_clear_on_exit_summary,
                    current.clearOnExit,
                ) {
                    onEvent(SettingsUiEvent.SetClearOnExit(it))
                }
                ToggleRow(
                    R.string.settings_diagnostics,
                    R.string.settings_diagnostics_summary,
                    current.diagnosticsConsent,
                ) {
                    onEvent(SettingsUiEvent.SetDiagnostics(it))
                }
                if (showAdPrivacy) {
                    ActionRow(
                        R.string.settings_ad_privacy,
                        R.string.settings_ad_privacy_summary,
                        onClick = onOpenAdPrivacy,
                    )
                }
                ActionRow(
                    R.string.settings_delete_all,
                    R.string.settings_delete_all_summary,
                    modifier = Modifier.testTag(SettingsTestTags.DELETE_ALL),
                ) { confirmDelete = true }

                SectionTitle(R.string.settings_section_about)
                ValueRow(R.string.settings_version, versionName, onClick = null)
                if (state.privacyPolicyUrl.isNotEmpty()) {
                    ActionRow(R.string.settings_privacy_policy, null) { onOpenLink(state.privacyPolicyUrl) }
                }
                ActionRow(R.string.settings_filter_lists, R.string.settings_filter_lists_credit) {
                    onOpenLink(FILTER_LICENCE_URL)
                }
                Spacer(Modifier.padding(8.dp))
            }
            SnackbarHost(snackbars)
        }
    }

    when (picker) {
        PickerKind.THEME -> PickerDialog(
            R.string.settings_theme,
            ThemeMode.entries,
            current.themeMode,
            { stringResource(it.labelRes()) },
            { onEvent(SettingsUiEvent.SetTheme(it)) },
        ) { picker = null }

        PickerKind.LANGUAGE -> PickerDialog(
            R.string.settings_language,
            AppLanguage.entries,
            current.language,
            { stringResource(it.labelRes()) },
            { onEvent(SettingsUiEvent.SetLanguage(it)) },
        ) { picker = null }

        PickerKind.SEARCH -> PickerDialog(
            R.string.settings_search_engine,
            SearchEngine.entries,
            current.searchEngine,
            { it.displayName },
            { onEvent(SettingsUiEvent.SetSearchEngine(it)) },
        ) { picker = null }

        null -> Unit
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_browsing),
            message = stringResource(R.string.settings_clear_browsing_confirm),
            confirmLabel = stringResource(CoreUiR.string.action_clear_all),
            onConfirm = {
                onEvent(SettingsUiEvent.ClearBrowsingData)
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

private const val FILTER_LICENCE_URL = "https://easylist.to/pages/licence.html"

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.HINDI -> R.string.language_hindi
    AppLanguage.MARATHI -> R.string.language_marathi
    AppLanguage.RUSSIAN -> R.string.language_russian
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActionRow(title: Int, summary: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValueRow(title: Int, value: String, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: Int, summary: Int, checked: Boolean, tag: String = "", onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .testTag(tag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> PickerDialog(
    title: Int,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(option)
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.action_cancel)) } },
    )
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@ThemePreviews
@Composable
private fun SettingsPreview() {
    ThetaTheme {
        SettingsScreen(SettingsUiState(), "0.1.0", true, {}, {}, {}, {}, {})
    }
}
