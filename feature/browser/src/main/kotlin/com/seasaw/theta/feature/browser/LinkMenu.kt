package com.seasaw.theta.feature.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seasaw.theta.core.ui.ThetaIcons

/** What to do with a long-pressed link. Opening in a new tab leaves the current page where it is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkMenu(url: String, fromPrivateTab: Boolean, onEvent: (BrowserUiEvent) -> Unit) {
    val context = LocalContext.current
    fun choose(action: () -> Unit): () -> Unit = {
        onEvent(BrowserUiEvent.DismissLinkMenu)
        action()
    }
    ModalBottomSheet(onDismissRequest = { onEvent(BrowserUiEvent.DismissLinkMenu) }) {
        Text(
            url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LinkRow(
            if (fromPrivateTab) R.string.link_open_private_new_tab else R.string.link_open_new_tab,
            ThetaIcons.Plus,
        ) { onEvent(BrowserUiEvent.OpenLink(url, LinkTarget.NEW_TAB)) }
        LinkRow(R.string.link_open_in_group, ThetaIcons.NewGroup) {
            onEvent(BrowserUiEvent.OpenLink(url, LinkTarget.NEW_TAB_IN_GROUP))
        }
        if (!fromPrivateTab) {
            LinkRow(R.string.link_open_private, ThetaIcons.NewPrivateTab) {
                onEvent(BrowserUiEvent.OpenLink(url, LinkTarget.INCOGNITO_TAB))
            }
        }
        LinkRow(
            R.string.link_copy,
            ThetaIcons.Copy,
            choose {
                context.copyLink(url)
                // Android 13 and later confirm a copy themselves.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onEvent(BrowserUiEvent.LinkCopied)
            },
        )
        LinkRow(R.string.link_share, ThetaIcons.Share, choose { context.shareLink(url) })
        androidx.compose.foundation.layout.Spacer(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun LinkRow(label: Int, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}

private fun Context.copyLink(url: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(url, url))
}

private fun Context.shareLink(url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    startActivity(Intent.createChooser(send, null))
}
