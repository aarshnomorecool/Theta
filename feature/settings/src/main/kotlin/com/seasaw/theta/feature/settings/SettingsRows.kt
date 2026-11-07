package com.seasaw.theta.feature.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.seasaw.theta.core.ui.CardGroup
import com.seasaw.theta.core.ui.CardRow
import com.seasaw.theta.core.ui.RowChevron
import com.seasaw.theta.core.ui.SectionHeader

/** A row with a switch. The whole row toggles, which is a far bigger target than the switch alone. */
@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
    tag: String = "",
) {
    CardRow(
        title = title,
        summary = summary,
        icon = icon,
        modifier = Modifier
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
internal fun LinkRow(
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
    tag: String = "",
) {
    CardRow(
        title = title,
        summary = summary,
        icon = icon,
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        trailing = { RowChevron() },
    )
}

@Composable
internal fun RadioRow(title: String, selected: Boolean, onSelect: () -> Unit, summary: String? = null) {
    CardRow(
        title = title,
        summary = summary,
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        trailing = { RadioButton(selected = selected, onClick = null) },
    )
}

@Composable
internal fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, summary: String? = null) {
    CardRow(
        title = title,
        summary = summary,
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        trailing = { Checkbox(checked = checked, onCheckedChange = null) },
    )
}

/** A header and its cards, the building block of every settings page. */
@Composable
internal fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) SectionHeader(title) else Spacer(Modifier.height(16.dp))
    CardGroup(content = content)
}

/** Explains a section in a sentence, under its cards. */
@Composable
internal fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 10.dp),
    )
}
