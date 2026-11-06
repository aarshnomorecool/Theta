package com.seasaw.theta.feature.newtab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasaw.theta.core.ads.AdPlacement
import com.seasaw.theta.core.ads.AdSlot
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.SiteAvatar
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaTheme

@Composable
fun NewTabRoute(onOpenUrl: (String) -> Unit, viewModel: NewTabViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewTabScreen(
    state: NewTabUiState,
    onOpenUrl: (String) -> Unit,
    onEvent: (NewTabUiEvent) -> Unit,
    adSlot: @Composable () -> Unit,
) {
    var isAdding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<SpeedDialSite?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.speed_dial_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = SPEED_DIAL_COLUMNS,
        ) {
            state.speedDial.forEach { site ->
                SpeedDialTile(site, Modifier.weight(1f), { onOpenUrl(site.url) }, { removing = site })
            }
            AddTile(Modifier.weight(1f)) { isAdding = true }
            repeat(blankTilesNeeded(state.speedDial.size + 1)) { Spacer(Modifier.weight(1f)) }
        }
        if (state.recentSites.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.recent_sites_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            state.recentSites.forEach { site -> RecentRow(site) { onOpenUrl(site.url) } }
        }
        adSlot()
    }

    if (isAdding) {
        AddSiteDialog(
            onAdd = { url, name ->
                onEvent(NewTabUiEvent.AddSpeedDial(url, name))
                isAdding = false
            },
            onDismiss = { isAdding = false },
        )
    }
    removing?.let { site ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(site.title) },
            text = { Text(stringResource(R.string.speed_dial_remove_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(NewTabUiEvent.RemoveSpeedDial(site.id))
                        removing = null
                    },
                ) { Text(stringResource(CoreUiR.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(CoreUiR.string.action_cancel)) }
            },
        )
    }
}

private const val SPEED_DIAL_COLUMNS = 4

/** Keeps the last row's tiles the same width as the rows above it. */
private fun blankTilesNeeded(tileCount: Int): Int =
    (SPEED_DIAL_COLUMNS - tileCount % SPEED_DIAL_COLUMNS) % SPEED_DIAL_COLUMNS

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialTile(site: SpeedDialSite, modifier: Modifier, onOpen: () -> Unit, onRemove: () -> Unit) {
    Column(
        modifier.combinedClickable(onClick = onOpen, onLongClick = onRemove),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SiteAvatar(site.title, size = 48.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            site.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddTile(modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.speed_dial_add), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun RecentRow(site: RecentSite, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteAvatar(UrlUtils.hostOf(site.url)?.removePrefix("www.") ?: site.title, size = 32.dp)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

@ThemePreviews
@Composable
private fun NewTabPreview() {
    ThetaTheme {
        NewTabScreen(
            state = NewTabUiState(
                speedDial = listOf(
                    SpeedDialSite(1, "https://news.example.in", "News"),
                    SpeedDialSite(2, "https://mail.example.in", "Mail"),
                    SpeedDialSite(3, "https://trains.example.in", "Trains"),
                ),
                recentSites = listOf(RecentSite("https://weather.example.in/pune", "Weather in Pune")),
            ),
            onOpenUrl = {},
            onEvent = {},
            adSlot = { AdSlot(AdPlacement.NEW_TAB) },
        )
    }
}
