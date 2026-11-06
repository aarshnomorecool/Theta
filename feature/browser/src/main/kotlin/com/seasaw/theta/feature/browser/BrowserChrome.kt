package com.seasaw.theta.feature.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme

internal object BrowserTestTags {
    const val ADDRESS_FIELD = "address_field"
    const val TAB_COUNT = "tab_count"
    const val NEW_TAB_BUTTON = "new_tab_button"
    const val MENU_BUTTON = "menu_button"
}

/**
 * The address bar. Shows the short form of the address until focused, then the full URL selected, so that typing
 * replaces it. Text state lives here because it changes on every keystroke and nothing else needs it until submit.
 */
@Composable
internal fun AddressBar(
    url: String,
    isIncognito: Boolean,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue(UrlUtils.displayUrl(url))) }
    // Follows the page while the user is not typing. A redirect mid-typing must not wipe what they have entered.
    LaunchedEffect(url, isEditing) {
        if (!isEditing) text = TextFieldValue(UrlUtils.displayUrl(url))
    }
    // Focus shows the full address, selected, so that typing replaces it. This waits a frame because the tap that gave
    // the field focus sets its cursor afterwards, which would otherwise clear the selection straight away.
    LaunchedEffect(isEditing) {
        if (isEditing) {
            withFrameNanos { }
            text = TextFieldValue(url, selection = TextRange(0, url.length))
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isIncognito) colors.secondaryContainer else colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isIncognito -> Icon(
                ThetaIcons.Incognito,
                stringResource(R.string.incognito_mode),
                modifier = Modifier.size(18.dp),
                tint = colors.onSecondaryContainer,
            )

            url.startsWith("https://") -> Icon(
                Icons.Filled.Lock,
                stringResource(R.string.connection_secure),
                modifier = Modifier.size(16.dp),
                tint = colors.onSurfaceVariant,
            )

            url.startsWith("http://") -> Icon(
                Icons.Filled.Warning,
                stringResource(R.string.connection_not_secure),
                modifier = Modifier.size(16.dp),
                tint = colors.error,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    onSubmit(text.text)
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
                },
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .testTag(BrowserTestTags.ADDRESS_FIELD)
                .onFocusChanged { focus -> isEditing = focus.isFocused },
            decorationBox = { field ->
                if (text.text.isEmpty()) {
                    Text(
                        stringResource(if (isIncognito) R.string.address_hint_incognito else R.string.address_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                field()
            },
        )
        when {
            isEditing && text.text.isNotEmpty() -> IconButton(onClick = {
                text = TextFieldValue()
            }, Modifier.size(32.dp)) {
                Icon(Icons.Filled.Clear, stringResource(R.string.clear_address), Modifier.size(18.dp))
            }

            isLoading -> IconButton(onClick = onStop, Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.stop_loading), Modifier.size(18.dp))
            }

            url.isNotEmpty() -> IconButton(onClick = onReload, Modifier.size(32.dp)) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.reload), Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun PageProgress(progress: Int, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress / BrowserTab.PROGRESS_DONE.toFloat() },
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

/** Sits at the bottom, where a thumb can reach it on a large cheap phone. */
@Composable
internal fun BrowserToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onShowTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.go_forward))
            }
            IconButton(onClick = onNewTab, Modifier.testTag(BrowserTestTags.NEW_TAB_BUTTON)) {
                Icon(Icons.Filled.Add, stringResource(R.string.new_tab))
            }
            IconButton(onClick = onShowTabs, Modifier.testTag(BrowserTestTags.TAB_COUNT)) {
                TabCountBadge(tabCount)
            }
            IconButton(onClick = onMenu, Modifier.testTag(BrowserTestTags.MENU_BUTTON)) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
            }
        }
    }
}

@Composable
private fun TabCountBadge(count: Int) {
    val label = stringResource(R.string.show_tabs, count)
    Box(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = label }
            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.coerceAtMost(MAX_BADGE_COUNT).toString(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private const val MAX_BADGE_COUNT = 99

@Composable
internal fun FindBar(
    find: FindState,
    onQuery: (String) -> Unit,
    onStep: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = find.query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp),
            decorationBox = { field ->
                if (find.query.isEmpty()) {
                    Text(stringResource(R.string.find_in_page), color = colors.onSurfaceVariant)
                }
                field()
            },
        )
        if (find.query.isNotEmpty()) {
            val position = if (find.totalMatches == 0) 0 else find.activeMatch + 1
            Text(
                stringResource(R.string.find_count, position, find.totalMatches),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = {
            onStep(false)
        }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.find_previous)) }
        IconButton(onClick = {
            onStep(true)
        }) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.find_next)) }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.find_close)) }
    }
}

/** A native page rather than HTML, so that it follows the theme and language and needs no WebView to render. */
@Composable
internal fun PageErrorView(isSslError: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Warning,
                null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            stringResource(if (isSslError) R.string.error_ssl_title else R.string.error_network_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            stringResource(if (isSslError) R.string.error_ssl_message else R.string.error_network_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // There is deliberately no "continue anyway" for certificate errors. That path must not exist in this app.
        if (!isSslError) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@ThemePreviews
@Composable
private fun ChromePreview() {
    ThetaTheme {
        Column {
            AddressBar("https://news.example.co.in/today", false, false, {}, {}, {})
            PageProgress(60)
            BrowserToolbar(true, false, 3, {}, {}, {}, {}, {})
            FindBar(FindState("cricket", 1, 4), {}, {}, {})
        }
    }
}

@ThemePreviews
@Composable
private fun PageErrorPreview() {
    ThetaTheme { PageErrorView(isSslError = true, onRetry = {}) }
}
