package com.seasaw.theta.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.SiteAvatar
import com.seasaw.theta.core.ui.ThetaIcons

internal object HistoryTestTags {
    const val GROUP_TOGGLE = "history_group_toggle"
    const val SITE_PREFIX = "history_site_"
    const val DELETE_SITE_PREFIX = "history_delete_site_"
}

/** One visit. Inside an open site group it is indented, so it reads as part of that group. */
@Composable
internal fun VisitRow(row: HistoryRow, onOpen: () -> Unit, onDelete: () -> Unit, indent: Dp = 0.dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp + indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteAvatar(
            UrlUtils.siteOf(row.url) ?: row.title,
            size = if (indent >
                0.dp
            ) {
                32.dp
            } else {
                40.dp
            },
        )
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                UrlUtils.displayUrl(row.url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(ThetaIcons.Close, stringResource(CoreUiR.string.action_delete))
        }
    }
}

/** A site with several visits. Tapping it shows or hides the visits underneath. */
@Composable
internal fun SiteRow(group: HistoryGroup, isExpanded: Boolean, onToggle: () -> Unit, onDeleteSite: () -> Unit) {
    val expandLabel = stringResource(if (isExpanded) R.string.history_hide_visits else R.string.history_show_visits)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = expandLabel, onClick = onToggle)
            .testTag(HistoryTestTags.SITE_PREFIX + group.site)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteAvatar(group.site)
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                group.site,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.history_site_visits, group.rows.size, group.rows.size) +
                    " · " + group.latest.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            if (isExpanded) ThetaIcons.ChevronUp else ThetaIcons.ChevronDown,
            contentDescription = expandLabel,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onDeleteSite, Modifier.testTag(HistoryTestTags.DELETE_SITE_PREFIX + group.site)) {
            Icon(ThetaIcons.Trash, stringResource(R.string.history_delete_site))
        }
    }
}
