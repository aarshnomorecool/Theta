package com.seasaw.theta.feature.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

/** What the address bar says about the connection. */
private enum class Security { NONE, SECURE, NOT_SECURE, CERTIFICATE_ERROR }

/** The URL can still say https after its certificate failed, so the error is checked first: a lock there would lie. */
private fun securityOf(url: String, isCertificateError: Boolean): Security = when {
    isCertificateError -> Security.CERTIFICATE_ERROR
    url.startsWith("https://") -> Security.SECURE
    url.startsWith("http://") -> Security.NOT_SECURE
    else -> Security.NONE
}

/**
 * What the bar shows while not being edited: only the host. A long path, credentials or a long subdomain such as
 * `bank.com.secure-login.example` could otherwise push the real site out of view.
 */
private fun shortAddress(url: String): String = UrlUtils.displayHost(url) ?: UrlUtils.displayUrl(url)

/**
 * The address bar. Shows the site's host until focused, then the full URL selected, so that typing replaces it. Text
 * state lives here because it changes on every keystroke and nothing else needs it until submit.
 *
 * @param isCertificateError the page failed its certificate check, which removes the lock even on https
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
    isCertificateError: Boolean = false,
) {
    var isEditing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue(shortAddress(url))) }
    // Follows the page while the user is not typing. A redirect mid-typing must not wipe what they have entered.
    LaunchedEffect(url, isEditing) {
        if (!isEditing) text = TextFieldValue(shortAddress(url))
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

    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(shape)
            .background(if (isIncognito) colors.secondaryContainer else colors.surfaceContainerHigh)
            .border(1.dp, if (isEditing) colors.primary else colors.outlineVariant, shape)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Private mode is shown beside the security state, never instead of it: an http page is just as readable
        // by the network in a private tab.
        if (isIncognito) {
            Icon(
                ThetaIcons.Private,
                stringResource(R.string.incognito_mode),
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp),
                tint = colors.onSecondaryContainer,
            )
        }
        SecurityIcon(securityOf(url, isCertificateError))
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
                val showsHost = !isEditing && text.text.isNotEmpty()
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.text.isEmpty()) {
                        Text(
                            stringResource(if (isIncognito) R.string.address_hint_incognito else R.string.address_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showsHost) HostLabel(text.text)
                    // The field stays in place, invisible, so it keeps its focus handling and its text for
                    // accessibility. The label above can do what a text field cannot: keep the end in view.
                    Box(Modifier.alpha(if (showsHost) 0f else 1f)) { field() }
                }
            },
        )
        when {
            isEditing && text.text.isNotEmpty() -> IconButton(onClick = {
                text = TextFieldValue()
            }, Modifier.size(32.dp)) {
                Icon(ThetaIcons.Close, stringResource(R.string.clear_address), Modifier.size(18.dp))
            }

            isLoading -> IconButton(onClick = onStop, Modifier.size(32.dp)) {
                Icon(ThetaIcons.Close, stringResource(R.string.stop_loading), Modifier.size(18.dp))
            }

            url.isNotEmpty() -> IconButton(onClick = onReload, Modifier.size(32.dp)) {
                Icon(ThetaIcons.Reload, stringResource(R.string.reload), Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SecurityIcon(security: Security) {
    val colors = MaterialTheme.colorScheme
    val (icon, label, tint) = when (security) {
        Security.NONE -> return
        Security.SECURE -> Triple(ThetaIcons.Lock, R.string.connection_secure, colors.onSurfaceVariant)
        Security.NOT_SECURE -> Triple(ThetaIcons.Warning, R.string.connection_not_secure, colors.error)
        Security.CERTIFICATE_ERROR -> Triple(ThetaIcons.Warning, R.string.connection_not_secure, colors.error)
    }
    Icon(icon, stringResource(label), modifier = Modifier.size(16.dp), tint = tint)
}

/**
 * The host with the registrable domain emphasised, ellipsised at the start rather than the end, so the part that says
 * which site this really is always stays visible: "…login.example.com", never "bank.com.login.ex…".
 */
@Composable
private fun HostLabel(host: String) {
    val colors = MaterialTheme.colorScheme
    val site = remember(host) { UrlUtils.registrableDomain(host) }
    val label = remember(host, site, colors) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(host.removeSuffix(site)) }
            withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Medium)) { append(site) }
        }
    }
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.StartEllipsis,
        // Purely visual: the text field underneath already carries the same text for accessibility.
        modifier = Modifier.clearAndSetSemantics { },
    )
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

/**
 * A floating dock at the bottom, where a thumb can reach it on a large cheap phone. The new-tab button is the one
 * filled, accent-coloured control, so the most common action is also the easiest to find.
 */
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
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = shape,
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            Modifier.height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(ThetaIcons.Back, stringResource(R.string.go_back))
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(ThetaIcons.Forward, stringResource(R.string.go_forward))
            }
            FilledIconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .size(width = 52.dp, height = 40.dp)
                    .testTag(BrowserTestTags.NEW_TAB_BUTTON),
                shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(ThetaIcons.Plus, stringResource(R.string.new_tab), Modifier.size(22.dp))
            }
            IconButton(onClick = onShowTabs, Modifier.testTag(BrowserTestTags.TAB_COUNT)) {
                TabCountBadge(tabCount)
            }
            IconButton(onClick = onMenu, Modifier.testTag(BrowserTestTags.MENU_BUTTON)) {
                Icon(ThetaIcons.More, stringResource(R.string.more_options))
            }
        }
    }
}

@Composable
private fun TabCountBadge(count: Int) {
    val label = stringResource(R.string.show_tabs, count)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 22.dp)
            .semantics { contentDescription = label }
            .clip(RoundedCornerShape(8.dp))
            .background(colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.coerceAtMost(MAX_BADGE_COUNT).toString(),
            color = colors.onSecondaryContainer,
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
        }) { Icon(ThetaIcons.ChevronUp, stringResource(R.string.find_previous)) }
        IconButton(onClick = {
            onStep(true)
        }) { Icon(ThetaIcons.ChevronDown, stringResource(R.string.find_next)) }
        IconButton(onClick = onClose) { Icon(ThetaIcons.Close, stringResource(R.string.find_close)) }
    }
}

/** A native page rather than HTML, so that it follows the theme and language and needs no WebView to render. */
@Composable
internal fun PageErrorView(
    isSslError: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isCrash: Boolean = false,
) {
    val title = when {
        isSslError -> R.string.error_ssl_title
        isCrash -> R.string.error_crashed_title
        else -> R.string.error_network_title
    }
    val message = when {
        isSslError -> R.string.error_ssl_message
        isCrash -> R.string.error_crashed_message
        else -> R.string.error_network_message
    }
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
                ThetaIcons.Warning,
                null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            stringResource(message),
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
