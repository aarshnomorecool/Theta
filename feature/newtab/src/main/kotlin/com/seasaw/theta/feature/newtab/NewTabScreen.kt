package com.seasaw.theta.feature.newtab

import android.content.Context
import android.icu.text.CompactDecimalFormat
import android.icu.text.NumberFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasaw.theta.core.ads.AdPlacement
import com.seasaw.theta.core.ads.AdSlot
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.data.PhotoCredit
import com.seasaw.theta.core.data.PrivacyStats
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.SiteIcon
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaMark
import com.seasaw.theta.core.ui.ThetaTheme
import java.util.Locale

internal object NewTabTestTags {
    const val STATS = "ntp_stats"
    const val PHOTO_CREDIT = "ntp_photo_credit"
    const val ADD_TILE = "ntp_add_tile"
}

@Composable
fun NewTabRoute(onOpenUrl: (String) -> Unit, viewModel: NewTabViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Runs each time a new-tab page comes on screen, so each one gets the next photo.
    LaunchedEffect(Unit) { viewModel.onEvent(NewTabUiEvent.Shown) }
    NewTabScreen(state, onOpenUrl, viewModel::onEvent) {
        AdSlot(AdPlacement.NEW_TAB, Modifier.padding(top = 24.dp))
    }
}

/**
 * Stateless, so previews and tests can run without Hilt.
 *
 * @param adSlot sits last, far from the address bar and toolbar, so that it neither moves content when it loads nor
 * invites accidental taps (ads rule 5).
 */
@Composable
internal fun NewTabScreen(
    state: NewTabUiState,
    onOpenUrl: (String) -> Unit,
    onEvent: (NewTabUiEvent) -> Unit,
    adSlot: @Composable () -> Unit,
) {
    var isAdding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<SpeedDialSite?>(null) }
    var pinning by remember { mutableStateOf<RecentSite?>(null) }
    val photo = state.wallpaper
    val onPhoto = photo != null

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (photo != null) {
            val image = remember(photo) { photo.image.asImageBitmap() }
            Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Darkens the photo just enough for white text to stay readable on bright skies and snow.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = SCRIM_TOP),
                            1f to Color.Black.copy(alpha = SCRIM_BOTTOM),
                        ),
                    ),
            )
        }
        CompositionLocalProvider(
            LocalContentColor provides if (onPhoto) Color.White else MaterialTheme.colorScheme.onBackground,
        ) {
            // At least a screen tall, so the photo credit sits at the bottom of a short page, yet scrolls with a
            // long one instead of covering it.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        NewTabContent(
                            state = state,
                            onPhoto = onPhoto,
                            onOpenUrl = onOpenUrl,
                            adSlot = adSlot,
                            onRemove = { removing = it },
                            onPin = { pinning = it },
                            onAdd = { isAdding = true },
                        )
                    }
                    if (photo != null) {
                        PhotoCreditChip(
                            credit = photo.credit,
                            onClick = { onOpenUrl(photo.credit.sourceUrl) },
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
            }
        }
    }

    NewTabDialogs(
        isAdding = isAdding,
        removing = removing,
        pinning = pinning,
        onEvent = onEvent,
        onClose = {
            isAdding = false
            removing = null
            pinning = null
        },
    )
}

@Composable
private fun NewTabContent(
    state: NewTabUiState,
    onPhoto: Boolean,
    onOpenUrl: (String) -> Unit,
    adSlot: @Composable () -> Unit,
    onRemove: (SpeedDialSite) -> Unit,
    onPin: (RecentSite) -> Unit,
    onAdd: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) { ThetaMark() }
    val stats = state.stats
    if (state.showStats && stats != null) {
        PrivacyStatsStrip(stats, onPhoto)
        Spacer(Modifier.height(24.dp))
    }
    SectionLabel(
        stringResource(
            if (state.speedDial.isEmpty()) R.string.top_sites_title else R.string.speed_dial_title,
        ),
    )
    Spacer(Modifier.height(12.dp))
    SitesGrid(state = state, onOpenUrl = onOpenUrl, onRemove = onRemove, onPin = onPin, onAdd = onAdd)
    if (state.showRecent && state.recentSites.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.recent_sites_title))
        Spacer(Modifier.height(8.dp))
        GlassCard(onPhoto) {
            state.recentSites.forEach { site -> RecentRow(site) { onOpenUrl(site.url) } }
        }
    }
    adSlot()
}

@Composable
private fun NewTabDialogs(
    isAdding: Boolean,
    removing: SpeedDialSite?,
    pinning: RecentSite?,
    onEvent: (NewTabUiEvent) -> Unit,
    onClose: () -> Unit,
) {
    if (isAdding) {
        AddSiteDialog(
            onAdd = { url, name ->
                onEvent(NewTabUiEvent.AddSpeedDial(url, name))
                onClose()
            },
            onDismiss = onClose,
        )
    }
    removing?.let { site ->
        SimpleDialog(
            title = site.title,
            message = stringResource(R.string.speed_dial_remove_message),
            confirmLabel = stringResource(CoreUiR.string.action_delete),
            onConfirm = {
                onEvent(NewTabUiEvent.RemoveSpeedDial(site.id))
                onClose()
            },
            onDismiss = onClose,
        )
    }
    pinning?.let { site ->
        SimpleDialog(
            title = stringResource(R.string.pin_title),
            message = stringResource(R.string.pin_message, site.title),
            confirmLabel = stringResource(R.string.speed_dial_add),
            onConfirm = {
                onEvent(NewTabUiEvent.AddSpeedDial(site.url, site.title))
                onClose()
            },
            onDismiss = onClose,
        )
    }
}

@Composable
private fun SimpleDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.action_cancel)) } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
    )
}

/** Dark glass over a photo, a plain raised surface without one. */
@Composable
private fun GlassCard(onPhoto: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                if (onPhoto) Color.Black.copy(alpha = GLASS_ALPHA) else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .padding(16.dp),
    ) { content() }
}

/**
 * A slim row of three figures under one label. Worth seeing at a glance, but it should not crowd out the sites people
 * came to this page to open.
 */
@Composable
private fun PrivacyStatsStrip(stats: PrivacyStats, onPhoto: Boolean) {
    val context = LocalContext.current
    val accent = if (onPhoto) Color(ACCENT_ON_PHOTO) else MaterialTheme.colorScheme.tertiary
    val since = stats.countingSince?.let {
        stringResource(R.string.stats_since, DateUtils.formatDateTime(context, it, DateUtils.FORMAT_ABBREV_MONTH))
    }
    val container = if (onPhoto) {
        Color.Black.copy(alpha = GLASS_ALPHA)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Column(Modifier.testTag(NewTabTestTags.STATS)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(container)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Stat(
                ThetaIcons.ShieldCheck,
                compactCount(stats.blockedRequests),
                stringResource(R.string.stats_blocked),
                accent,
                Modifier.weight(1f),
            )
            Stat(
                ThetaIcons.DataSaved,
                Formatter.formatShortFileSize(context, stats.estimatedBytesSaved),
                stringResource(R.string.stats_data),
                LocalContentColor.current,
                Modifier.weight(1f),
            )
            Stat(
                ThetaIcons.Timer,
                timeSaved(context, stats.estimatedMillisSaved),
                stringResource(R.string.stats_time),
                LocalContentColor.current,
                Modifier.weight(1f),
            )
        }
        Text(
            listOfNotNull(since, stringResource(R.string.stats_estimated)).joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = valueColor, modifier = Modifier.size(16.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
            maxLines = 2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SitesGrid(
    state: NewTabUiState,
    onOpenUrl: (String) -> Unit,
    onRemove: (SpeedDialSite) -> Unit,
    onPin: (RecentSite) -> Unit,
    onAdd: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = COLUMNS,
    ) {
        state.speedDial.forEach { site ->
            SiteTile(site.title, site.url, site.icon, Modifier.weight(1f), { onOpenUrl(site.url) }, { onRemove(site) })
        }
        state.topSites.forEach { site ->
            val label = site.title.ifBlank { UrlUtils.displayUrl(site.url) }
            SiteTile(label, site.url, site.icon, Modifier.weight(1f), { onOpenUrl(site.url) }, { onPin(site) })
        }
        AddTile(Modifier.weight(1f), onAdd)
        repeat(blankTilesNeeded(state.speedDial.size + state.topSites.size + 1)) { Spacer(Modifier.weight(1f)) }
    }
}

private const val COLUMNS = 4

/** Keeps the last row's tiles the same width as the rows above it. */
private fun blankTilesNeeded(tileCount: Int): Int = (COLUMNS - tileCount % COLUMNS) % COLUMNS

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(
    title: String,
    url: String,
    icon: android.graphics.Bitmap?,
    modifier: Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val favicon = remember(icon) { icon?.asImageBitmap() }
        SiteIcon(UrlUtils.hostOf(url)?.let(UrlUtils::registrableDomain) ?: title, favicon = favicon, size = 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 80.dp),
        )
    }
}

@Composable
private fun AddTile(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(NewTabTestTags.ADD_TILE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(LocalContentColor.current.copy(alpha = ADD_TILE_ALPHA)),
            contentAlignment = Alignment.Center,
        ) { Icon(ThetaIcons.Plus, null) }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.speed_dial_add), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun RecentRow(site: RecentSite, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val favicon = remember(site.icon) { site.icon?.asImageBitmap() }
        SiteIcon(
            UrlUtils.hostOf(site.url)?.let(UrlUtils::registrableDomain) ?: site.title,
            favicon = favicon,
            size = 36.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                site.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                UrlUtils.displayUrl(site.url),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Every photo is credited where it is shown. Tapping opens its page, which has the full licence. */
@Composable
private fun PhotoCreditChip(credit: PhotoCredit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.photo_credit_description, credit.title, credit.author, credit.licence)
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = GLASS_ALPHA))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(NewTabTestTags.PHOTO_CREDIT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ThetaIcons.Camera, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(
            stringResource(R.string.photo_credit, credit.author, credit.licence),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 6.dp)
                .widthIn(max = 260.dp),
        )
    }
}

@Composable
private fun AddSiteDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed_dial_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.speed_dial_address)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.speed_dial_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(address, name) }, enabled = address.isNotBlank()) {
                Text(stringResource(R.string.speed_dial_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.action_cancel)) } },
    )
}

/** Short and in the user's language: "1,234", then "12K" or "12 हज़ार" once the number gets long. */
internal fun compactCount(count: Long, locale: Locale = Locale.getDefault()): String = if (count < COMPACT_FROM) {
    NumberFormat.getIntegerInstance(locale).format(count)
} else {
    CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(count)
}

private fun timeSaved(context: Context, millis: Long): String {
    val seconds = millis / MILLIS_PER_SECOND
    return when {
        seconds < TENTHS_BELOW -> context.getString(
            R.string.stats_time_seconds_fraction,
            NumberFormat.getInstance().apply {
                maximumFractionDigits = 1
            }.format(millis / MILLIS_PER_SECOND.toDouble()),
        )

        seconds < SECONDS_PER_MINUTE -> context.getString(R.string.stats_time_seconds, seconds)

        seconds < SECONDS_PER_HOUR -> context.getString(R.string.stats_time_minutes, seconds / SECONDS_PER_MINUTE)

        else -> context.getString(
            R.string.stats_time_hours,
            NumberFormat.getInstance().apply {
                maximumFractionDigits = 1
            }.format(seconds / SECONDS_PER_HOUR.toDouble()),
        )
    }
}

private const val COMPACT_FROM = 10_000L
private const val TENTHS_BELOW = 10L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SCRIM_TOP = 0.25f
private const val SCRIM_BOTTOM = 0.6f
private const val GLASS_ALPHA = 0.42f
private const val SECONDARY_ALPHA = 0.75f
private const val ADD_TILE_ALPHA = 0.14f
private const val ACCENT_ON_PHOTO = 0xFF9FE8D2L

@ThemePreviews
@Composable
private fun NewTabPreview() {
    ThetaTheme {
        NewTabScreen(
            state = NewTabUiState(
                speedDial = listOf(
                    SpeedDialSite(1, "https://news.example.in", "News"),
                    SpeedDialSite(2, "https://mail.example.in", "Mail"),
                ),
                topSites = listOf(RecentSite("https://trains.example.in", "Trains")),
                recentSites = listOf(RecentSite("https://weather.example.in/pune", "Weather in Pune")),
                stats = PrivacyStats(blockedRequests = 12_480, countingSince = 1_760_000_000_000),
            ),
            onOpenUrl = {},
            onEvent = {},
            adSlot = {},
        )
    }
}
