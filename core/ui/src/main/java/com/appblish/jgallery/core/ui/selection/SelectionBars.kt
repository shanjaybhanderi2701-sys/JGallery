package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/** The bulk actions offered on the selection bar (spec §7.6). Drives E8's primitives. */
enum class BulkAction { COPY, MOVE, TRASH }

/**
 * Selection app-bar (spec §7.6): replaces the tab header while selection is active. Left close (X)
 * clears the selection; the title is the live "N selected" count; the trailing action is Select
 * All / Deselect All. Light chrome to match the reference look.
 */
@Composable
fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JGalleryColors.AccentSoft)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 4.dp)
            .testTag("selection_top_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("selection_close")) {
            Icon(Icons.Outlined.Close, contentDescription = "Exit selection", tint = JGalleryColors.Text)
        }
        Text(
            text = "$count selected",
            color = JGalleryColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("selection_count"),
        )
        Text(
            text = if (allSelected) "Deselect all" else "Select all",
            color = JGalleryColors.Accent,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .selectable(
                    selected = false,
                    role = Role.Button,
                    onClick = { if (allSelected) onDeselectAll() else onSelectAll() },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("selection_select_all"),
        )
    }
}

/**
 * Bottom bulk-action bar (spec §7.6): Copy to / Move to / Delete, each driving an E8 primitive via
 * [onAction]. Disabled while [enabled] is false (e.g. an op is already running). Shown only while a
 * selection exists.
 */
@Composable
fun BulkActionBar(
    enabled: Boolean,
    onAction: (BulkAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JGalleryColors.Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(72.dp)
            .padding(horizontal = 8.dp)
            .testTag("bulk_action_bar"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BulkActionButton("Copy to", Icons.Outlined.ContentCopy, enabled, "bulk_copy") { onAction(BulkAction.COPY) }
        BulkActionButton("Move to", Icons.Outlined.DriveFileMove, enabled, "bulk_move") { onAction(BulkAction.MOVE) }
        BulkActionButton("Delete", Icons.Outlined.Delete, enabled, "bulk_delete") { onAction(BulkAction.TRASH) }
    }
}

@Composable
private fun RowScope.BulkActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val tint = if (enabled) JGalleryColors.Text else JGalleryColors.TextSecondary
    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(selected = false, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
