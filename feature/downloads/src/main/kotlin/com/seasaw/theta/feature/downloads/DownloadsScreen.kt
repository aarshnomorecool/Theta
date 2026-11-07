package com.seasaw.theta.feature.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasaw.theta.core.ads.AdPlacement
import com.seasaw.theta.core.ads.AdSlot
import com.seasaw.theta.core.ui.EmptyState
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme
import com.seasaw.theta.core.ui.ThetaTopBar

@Composable
fun DownloadsRoute(onBack: () -> Unit, viewModel: DownloadsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DownloadsScreen(
        state = state,
        onOpen = { row -> context.openDownload(viewModel, row) },
        onBack = onBack,
        onEvent = viewModel::onEvent,
    ) { AdSlot(AdPlacement.DOWNLOADS) }
}

private fun Context.openDownload(viewModel: DownloadsViewModel, row: DownloadRow) {
    val uri = viewModel.uriFor(row.id)
    if (uri == null) {
        Toast.makeText(this, R.string.downloads_file_missing, Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, row.mimeType ?: contentResolver.getType(uri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(intent)
    } catch (ignored: ActivityNotFoundException) {
        Toast.makeText(this, R.string.downloads_no_app, Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun DownloadsScreen(
    state: DownloadsUiState,
    onOpen: (DownloadRow) -> Unit,
    onBack: () -> Unit,
    onEvent: (DownloadsUiEvent) -> Unit,
    adSlot: @Composable () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding()) {
            ThetaTopBar(stringResource(R.string.downloads_title), onBack)
            if (state.rows.isEmpty()) {
                EmptyState(
                    ThetaIcons.Download,
                    stringResource(R.string.downloads_empty_title),
                    stringResource(R.string.downloads_empty_message),
                    Modifier.weight(1f),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.rows, key = { it.id }) { row -> DownloadItem(row, onOpen, onEvent) }
                }
            }
            adSlot()
        }
    }
}

@Composable
private fun DownloadItem(row: DownloadRow, onOpen: (DownloadRow) -> Unit, onEvent: (DownloadsUiEvent) -> Unit) {
    val context = LocalContext.current
    val isDone = row.status == DownloadStatus.COMPLETE
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = isDone) { onOpen(row) }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ThetaIcons.Download, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                row.fileName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                statusText(row, context),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.status ==
                    DownloadStatus.FAILED
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (row.status == DownloadStatus.RUNNING) {
                val fraction = row.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().padding(top = 4.dp))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        IconButton(
            onClick = {
                onEvent(
                    if (row.status ==
                        DownloadStatus.RUNNING
                    ) {
                        DownloadsUiEvent.Cancel(row.id)
                    } else {
                        DownloadsUiEvent.Remove(row.id)
                    },
                )
            },
        ) {
            Icon(
                ThetaIcons.Close,
                stringResource(
                    if (row.status ==
                        DownloadStatus.RUNNING
                    ) {
                        CoreUiR.string.action_cancel
                    } else {
                        R.string.downloads_remove
                    },
                ),
            )
        }
    }
}

@Composable
private fun statusText(row: DownloadRow, context: Context): String = when (row.status) {
    DownloadStatus.COMPLETE -> Formatter.formatShortFileSize(context, row.bytesTotal)
    DownloadStatus.FAILED -> stringResource(R.string.downloads_failed)
    DownloadStatus.PAUSED -> stringResource(R.string.downloads_paused)
    DownloadStatus.RUNNING -> Formatter.formatShortFileSize(context, row.bytesDownloaded)
}

@ThemePreviews
@Composable
private fun DownloadsPreview() {
    ThetaTheme {
        DownloadsScreen(
            DownloadsUiState(
                listOf(
                    DownloadRow(1, "ticket.pdf", "application/pdf", DownloadStatus.COMPLETE, 240_000, 240_000),
                    DownloadRow(2, "song.mp3", "audio/mpeg", DownloadStatus.RUNNING, 1_200_000, 4_000_000),
                ),
            ),
            {},
            {},
            {},
        ) { AdSlot(AdPlacement.DOWNLOADS) }
    }
}
