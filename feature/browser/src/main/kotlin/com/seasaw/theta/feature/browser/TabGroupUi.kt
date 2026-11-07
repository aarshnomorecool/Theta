package com.seasaw.theta.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme

internal object TabGroupTestTags {
    const val NAME_FIELD = "group_name_field"
    const val SWATCH_PREFIX = "group_swatch_"
    const val CONFIRM = "group_confirm"
}

/** The group colours, tuned separately for dark and light so each stays readable on its background. */
internal object GroupPalette {
    private val dark = listOf(
        Color(0xFFBDC1C6), Color(0xFF8AB4F8), Color(0xFFF28B82), Color(0xFFFDD663), Color(0xFF81C995),
        Color(0xFFFF8BCB), Color(0xFFC58AF9), Color(0xFF78D9EC), Color(0xFFFCAD70),
    )
    private val light = listOf(
        Color(0xFF5F6368), Color(0xFF1A73E8), Color(0xFFD93025), Color(0xFFB06000), Color(0xFF188038),
        Color(0xFFD01884), Color(0xFF9334E6), Color(0xFF007B83), Color(0xFFC5530A),
    )
    private val names = listOf(
        R.string.group_color_grey, R.string.group_color_blue, R.string.group_color_red, R.string.group_color_yellow,
        R.string.group_color_green, R.string.group_color_pink, R.string.group_color_purple, R.string.group_color_cyan,
        R.string.group_color_orange,
    )

    @Composable
    fun color(index: Int): Color {
        val isDark = MaterialTheme.colorScheme.background.luminance() < HALF
        return (if (isDark) dark else light)[index.mod(TabGroupRules.COLOR_COUNT)]
    }

    fun nameRes(index: Int): Int = names[index.mod(TabGroupRules.COLOR_COUNT)]

    private const val HALF = 0.5f
}

/** A group's name, or "3 tabs" for a group that was never named. */
@Composable
internal fun groupLabel(group: TabGroup, tabCount: Int): String =
    group.name.ifBlank { pluralStringResource(R.plurals.tab_count, tabCount, tabCount) }

@Composable
internal fun GroupDot(colorIndex: Int, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(GroupPalette.color(colorIndex)),
    )
}

/** Names a new group or edits an existing one. */
@Composable
internal fun GroupEditorDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialColor: Int,
    onConfirm: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableIntStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.group_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TabGroupTestTags.NAME_FIELD),
                )
                Text(stringResource(R.string.group_color), style = MaterialTheme.typography.labelLarge)
                // Two short rows rather than a flow layout: nine swatches never fit one dialog row on small phones.
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        0 until SWATCHES_PER_ROW,
                        SWATCHES_PER_ROW until TabGroupRules.COLOR_COUNT,
                    ).forEach { range ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            range.forEach { index -> Swatch(index, index == color) { color = index } }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, Modifier.testTag(TabGroupTestTags.CONFIRM)) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.action_cancel)) } },
    )
}

@Composable
private fun Swatch(index: Int, isSelected: Boolean, onClick: () -> Unit) {
    val label = stringResource(GroupPalette.nameRes(index))
    val swatch = GroupPalette.color(index)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = isSelected
            }
            .testTag(TabGroupTestTags.SWATCH_PREFIX + index),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(ThetaIcons.Check, null, tint = Color.Black.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        }
    }
}

/** Picks an existing group for the chosen tabs, or starts a new one. */
@Composable
internal fun GroupPickerDialog(
    groups: List<Pair<TabGroup, Int>>,
    onPick: (TabGroup) -> Unit,
    onNewGroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_pick_title)) },
        text = {
            Column {
                groups.forEach { (group, count) ->
                    PickerRow(onClick = { onPick(group) }) {
                        GroupDot(group.colorIndex, size = 14.dp)
                        Text(
                            groupLabel(group, count),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        )
                        Text(
                            pluralStringResource(R.plurals.tab_count, count, count),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PickerRow(onClick = onNewGroup) {
                    Icon(ThetaIcons.NewGroup, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.group_pick_new),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.action_cancel)) } },
    )
}

@Composable
private fun PickerRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * The flow for "put these tabs in a group": straight to naming a new group when none exist yet, otherwise a choice
 * between the existing groups and a new one.
 */
@Composable
internal fun GroupTabsFlow(
    tabIds: Set<String>,
    isIncognito: Boolean,
    state: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    onDone: () -> Unit,
) {
    val candidates = state.groups
        .filter { it.isIncognito == isIncognito }
        .map { group -> group to state.tabs.count { it.groupId == group.id } }
    var naming by rememberSaveable { mutableStateOf(candidates.isEmpty()) }
    if (naming) {
        GroupEditorDialog(
            title = stringResource(R.string.group_new_title),
            confirmLabel = stringResource(R.string.action_create),
            initialName = "",
            initialColor = TabGroupRules.nextColor(state.groups),
            onConfirm = { name, color ->
                onEvent(BrowserUiEvent.CreateGroup(tabIds, name, color, isIncognito))
                onDone()
            },
            onDismiss = onDone,
        )
    } else {
        GroupPickerDialog(
            groups = candidates,
            onPick = { group ->
                onEvent(BrowserUiEvent.AddToGroup(tabIds, group.id))
                onDone()
            },
            onNewGroup = { naming = true },
            onDismiss = onDone,
        )
    }
}

private const val MAX_NAME_LENGTH = 40
private const val SWATCHES_PER_ROW = 5

@ThemePreviews
@Composable
private fun GroupEditorPreview() {
    ThetaTheme(darkTheme = true) {
        GroupEditorDialog("New tab group", "Create", "Cricket", 1, { _, _ -> }, {})
    }
}
