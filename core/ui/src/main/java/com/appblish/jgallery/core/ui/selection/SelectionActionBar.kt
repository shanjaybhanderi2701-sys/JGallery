package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * A contextual multi-select action (design G1-D6 / TB-04). Each action is either **multi-safe** —
 * valid over any selection ≥ 1, shown in the always-visible bottom action bar — or **single-only** —
 * valid only when exactly one item is selected, tucked behind the ⋮ overflow. [destructive] renders
 * the action in the destructive red. The host decides *which* actions are valid for its selection; this
 * enum is just the shared vocabulary shared by album-tab album selection and album-detail media
 * selection so the bar looks and behaves identically in both.
 */
enum class SelectionAction(
    val label: String,
    val icon: ImageVector,
    val single: Boolean,
    val destructive: Boolean,
    val tag: String,
) {
    PIN("Pin", Icons.Outlined.PushPin, single = false, destructive = false, tag = "selection_action_pin"),
    COPY("Copy", Icons.Outlined.ContentCopy, single = false, destructive = false, tag = "selection_action_copy"),
    MOVE("Move", Icons.Outlined.DriveFileMove, single = false, destructive = false, tag = "selection_action_move"),
    DELETE("Delete", Icons.Outlined.DeleteOutline, single = false, destructive = true, tag = "selection_action_delete"),
    RENAME("Rename", Icons.Outlined.DriveFileRenameOutline, single = true, destructive = false, tag = "selection_action_rename"),
    SET_COVER("Set as cover", Icons.Outlined.Image, single = true, destructive = false, tag = "selection_action_set_cover"),
    DETAILS("Details", Icons.Outlined.Info, single = true, destructive = false, tag = "selection_action_details"),
}

/**
 * The contextual bottom action bar for a multi-select (design G1-D6 / TB-04), shared by the Albums-tab
 * album selection and (via G1-10) album-detail media selection. [multiActions] are always visible and
 * enabled for any selection ≥ 1; [singleActions] live behind the ⋮ overflow and are enabled only when
 * exactly one item is selected **and** [isSingleActionEnabled] allows it — otherwise they stay listed
 * but dimmed with a "1 only" hint, so the menu never shifts and it teaches the single-item rule. Delete
 * renders destructive-red. The host reads [selectionCount] for the gate and supplies the valid ops.
 */
@Composable
fun SelectionActionBar(
    selectionCount: Int,
    multiActions: List<SelectionAction>,
    singleActions: List<SelectionAction>,
    onAction: (SelectionAction) -> Unit,
    modifier: Modifier = Modifier,
    isSingleActionEnabled: (SelectionAction) -> Boolean = { true },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JGalleryColors.Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(72.dp)
            .padding(horizontal = 8.dp)
            .testTag("selection_action_bar"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        multiActions.forEach { action ->
            MultiActionButton(
                action = action,
                enabled = selectionCount >= 1,
                onClick = { onAction(action) },
            )
        }
        if (singleActions.isNotEmpty()) {
            OverflowActions(
                actions = singleActions,
                isEnabled = { selectionCount == 1 && isSingleActionEnabled(it) },
                onAction = onAction,
            )
        }
    }
}

/** The ⋮ overflow hosting the single-only actions; disabled entries keep their slot and show a hint. */
@Composable
private fun RowScope.OverflowActions(
    actions: List<SelectionAction>,
    isEnabled: (SelectionAction) -> Boolean,
    onAction: (SelectionAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        LabeledIconButton(
            label = "More",
            icon = Icons.Outlined.MoreVert,
            tint = JGalleryColors.Text,
            enabled = true,
            tag = "selection_action_more",
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = "SINGLE ITEM ONLY",
                color = JGalleryColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            actions.forEach { action ->
                val enabled = isEnabled(action)
                DropdownMenuItem(
                    text = {
                        Text(action.label, color = if (enabled) JGalleryColors.Text else JGalleryColors.TextSecondary)
                    },
                    trailingIcon = if (!enabled) {
                        { Text("1 only", color = JGalleryColors.TextSecondary, fontSize = 11.sp) }
                    } else {
                        null
                    },
                    enabled = enabled,
                    onClick = { expanded = false; onAction(action) },
                    modifier = Modifier.testTag(action.tag),
                )
            }
        }
    }
}

/** A multi-safe action, weighted to share the bottom bar evenly with its peers. */
@Composable
private fun RowScope.MultiActionButton(
    action: SelectionAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    LabeledIconButton(
        label = action.label,
        icon = action.icon,
        tint = if (action.destructive) JGalleryColors.Destructive else JGalleryColors.Text,
        enabled = enabled,
        tag = action.tag,
        onClick = onClick,
        modifier = Modifier.weight(1f),
    )
}

/** One labelled icon button (icon over a 12sp label), laid out like the media grid's bulk-action buttons. */
@Composable
private fun LabeledIconButton(
    label: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolved = if (enabled) tint else JGalleryColors.TextSecondary
    Column(
        modifier = modifier
            .selectable(selected = false, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = resolved, modifier = Modifier.size(23.dp))
        Text(label, color = resolved, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
