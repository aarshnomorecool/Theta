package com.seasaw.theta.feature.browser

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.seasaw.theta.core.data.settings.UserSettings
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.webview.SitePermission
import kotlinx.coroutines.delay

/** Shows whichever question is pending. Nothing a page does can skip these. */
@Composable
internal fun PromptHost(
    prompt: BrowserPrompt?,
    onEvent: (BrowserUiEvent) -> Unit,
    onOpenExternal: (BrowserPrompt.OpenExternally) -> Unit,
) {
    when (prompt) {
        is BrowserPrompt.Permission -> PermissionDialog(prompt, onEvent)
        is BrowserPrompt.OpenExternally -> OpenAppDialog(prompt, onEvent, onOpenExternal)
        is BrowserPrompt.Download -> DownloadDialog(prompt, onEvent)
        null -> Unit
    }
}

private fun SitePermission.manifestPermissions(): List<String> = when (this) {
    SitePermission.CAMERA -> listOf(Manifest.permission.CAMERA)

    SitePermission.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)

    SitePermission.LOCATION -> listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
}

/**
 * The site's request is put to the user first. Only if they agree does Android's own permission dialog appear, so the
 * app never asks for camera or location on behalf of a site the user has not approved.
 */
@Composable
private fun PermissionDialog(prompt: BrowserPrompt.Permission, onEvent: (BrowserUiEvent) -> Unit) {
    val context = LocalContext.current
    var rememberChoice by remember { mutableStateOf(false) }
    val request = prompt.prompt
    val needed = request.permissions.flatMap { it.manifestPermissions() }
    val systemLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // Every requested kind needs one Android permission: camera alone must not grant a camera+mic request.
            // Location is satisfied by either the fine or the coarse permission.
            val granted = request.permissions.all { permission ->
                permission.manifestPermissions().any { name ->
                    results[name] == true ||
                        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
                }
            }
            onEvent(BrowserUiEvent.RespondPermission(granted, rememberChoice && granted))
        }
    val armed = rememberArmed()
    val labels = request.permissions.map { stringResource(it.labelRes()) }.joinToString()
    AlertDialog(
        onDismissRequest = { onEvent(BrowserUiEvent.RespondPermission(granted = false, remember = false)) },
        title = { Text(stringResource(R.string.permission_title, request.host)) },
        text = {
            Column {
                Text(stringResource(R.string.permission_message, labels))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { rememberChoice = !rememberChoice }
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Text(stringResource(R.string.permission_remember))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val missing = needed.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) {
                        onEvent(BrowserUiEvent.RespondPermission(granted = true, remember = rememberChoice))
                    } else {
                        systemLauncher.launch(missing.toTypedArray())
                    }
                },
                enabled = armed,
            ) { Text(stringResource(R.string.action_allow)) }
        },
        dismissButton = {
            TextButton(onClick = {
                onEvent(BrowserUiEvent.RespondPermission(granted = false, remember = rememberChoice))
            }) {
                Text(stringResource(R.string.action_block))
            }
        },
    )
}

private fun SitePermission.labelRes(): Int = when (this) {
    SitePermission.CAMERA -> R.string.permission_camera
    SitePermission.MICROPHONE -> R.string.permission_microphone
    SitePermission.LOCATION -> R.string.permission_location
}

/**
 * False for a moment after a dialog appears. A page can raise a dialog at the exact moment it expects a tap, so the
 * tap meant for the page would land on "Allow"; the delay means only a deliberate tap can say yes.
 */
@Composable
private fun rememberArmed(): Boolean {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PROMPT_ARMING_MILLIS)
        armed = true
    }
    return armed
}

private const val PROMPT_ARMING_MILLIS = 500L

/** Project rule: open other apps only after asking, and never auto-launch. */
@Composable
private fun OpenAppDialog(
    prompt: BrowserPrompt.OpenExternally,
    onEvent: (BrowserUiEvent) -> Unit,
    onOpenExternal: (BrowserPrompt.OpenExternally) -> Unit,
) {
    val armed = rememberArmed()
    AlertDialog(
        onDismissRequest = { onEvent(BrowserUiEvent.RespondOpenExternally(open = false)) },
        title = { Text(stringResource(R.string.open_app_title)) },
        text = { Text(stringResource(R.string.open_app_message, prompt.link.scheme)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onOpenExternal(prompt)
                    onEvent(BrowserUiEvent.RespondOpenExternally(open = true))
                },
                enabled = armed,
            ) { Text(stringResource(R.string.action_open)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(BrowserUiEvent.RespondOpenExternally(open = false)) }) {
                Text(stringResource(CoreUiR.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DownloadDialog(prompt: BrowserPrompt.Download, onEvent: (BrowserUiEvent) -> Unit) {
    val armed = rememberArmed()
    AlertDialog(
        onDismissRequest = { onEvent(BrowserUiEvent.RespondDownload(accept = false)) },
        title = { Text(stringResource(R.string.download_title)) },
        text = {
            val note = if (prompt.isIncognito) "\n\n" + stringResource(R.string.download_incognito_note) else ""
            Text(prompt.fileName + note)
        },
        confirmButton = {
            TextButton(onClick = { onEvent(BrowserUiEvent.RespondDownload(accept = true)) }, enabled = armed) {
                Text(stringResource(R.string.action_download))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(BrowserUiEvent.RespondDownload(accept = false)) }) {
                Text(stringResource(CoreUiR.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun TextSizeDialog(percent: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(percent.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_text_size)) },
        text = {
            Column {
                Text("${value.toInt()}%")
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    onValueChangeFinished = { onChange(value.toInt()) },
                    valueRange = UserSettings.MIN_TEXT_ZOOM.toFloat()..UserSettings.MAX_TEXT_ZOOM.toFloat(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.action_close)) } },
    )
}

/** Where the menu leads outside this screen. These belong to other features, so the app wires them up. */
data class BrowserNavigation(
    val onBookmarks: () -> Unit = {},
    val onHistory: () -> Unit = {},
    val onDownloads: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onClearBrowsingData: () -> Unit = {},
)

@Composable
internal fun BrowserMenu(
    expanded: Boolean,
    state: BrowserUiState,
    navigation: BrowserNavigation,
    onEvent: (BrowserUiEvent) -> Unit,
    onShare: () -> Unit,
    onTextSize: () -> Unit,
    onDismiss: () -> Unit,
    onAddToGroup: () -> Unit = {},
) {
    val tab = state.selectedTab
    val hasPage = tab != null && !tab.isNewTab
    fun chosen(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuRow(
            R.string.menu_new_incognito,
            ThetaIcons.NewPrivateTab,
            chosen {
                onEvent(BrowserUiEvent.NewTab(isIncognito = true))
            },
        )
        if (tab != null) {
            if (tab.groupId == null) {
                MenuRow(R.string.menu_add_to_group, ThetaIcons.NewGroup, chosen(onAddToGroup))
            } else {
                MenuRow(
                    R.string.menu_remove_from_group,
                    ThetaIcons.Ungroup,
                    chosen { onEvent(BrowserUiEvent.RemoveFromGroup(setOf(tab.id))) },
                )
            }
        }
        HorizontalDivider()
        if (hasPage) {
            MenuRow(
                if (state.isBookmarked) R.string.menu_remove_bookmark else R.string.menu_add_bookmark,
                if (state.isBookmarked) ThetaIcons.Bookmarked else ThetaIcons.Bookmark,
                chosen { onEvent(BrowserUiEvent.ToggleBookmark) },
            )
            MenuRow(R.string.menu_add_speed_dial, ThetaIcons.Star, chosen { onEvent(BrowserUiEvent.AddToSpeedDial) })
            MenuRow(R.string.menu_find, ThetaIcons.Find, chosen { onEvent(BrowserUiEvent.StartFind) })
            MenuRow(R.string.menu_share, ThetaIcons.Share, chosen(onShare))
            MenuToggleRow(R.string.menu_desktop_site, ThetaIcons.Desktop, tab.isDesktopMode) {
                onEvent(BrowserUiEvent.ToggleDesktopMode)
            }
            // Not offered in private tabs: the choice is saved on disk, which would record the private visit.
            if (state.isAdBlockEnabled && !tab.isIncognito) {
                MenuToggleRow(R.string.menu_allow_ads, ThetaIcons.Shield, state.isSiteExemptFromAdBlock) {
                    onEvent(BrowserUiEvent.ToggleSiteAdBlock)
                }
            }
            MenuRow(R.string.menu_text_size, ThetaIcons.TextSize, chosen(onTextSize))
            HorizontalDivider()
        }
        MenuToggleRow(R.string.menu_lite_mode, ThetaIcons.Lite, state.isLiteMode) {
            onEvent(BrowserUiEvent.ToggleLiteMode)
        }
        MenuRow(R.string.menu_bookmarks, ThetaIcons.Bookmark, chosen(navigation.onBookmarks))
        MenuRow(R.string.menu_history, ThetaIcons.History, chosen(navigation.onHistory))
        MenuRow(R.string.menu_downloads, ThetaIcons.Download, chosen(navigation.onDownloads))
        MenuRow(R.string.menu_settings, ThetaIcons.Settings, chosen(navigation.onSettings))
    }
}

@Composable
private fun MenuRow(label: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun MenuToggleRow(label: Int, icon: ImageVector, checked: Boolean, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        trailingIcon = { Switch(checked = checked, onCheckedChange = { onToggle() }) },
        onClick = onToggle,
    )
}
