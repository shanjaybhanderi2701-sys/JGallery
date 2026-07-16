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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.core.view.WindowCompat
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/** The bulk actions offered on the selection bar (spec §7.6). Drives E8's primitives. */
enum class BulkAction { COPY, MOVE, TRASH }

/**
 * Selection app-bar (spec §7.6, design G1-D7 item 9): replaces the tab header while selection is
 * active. Left close (X) clears the selection; the title is the live "N selected" count; the trailing
 * action is Select All / Deselect All.
 *
 * Item 9 / G1-D8 item 5 — no "half blue screen" status-bar bleed and no white status bar: the app
 * shell hosts every tab surface inside a Material3 [androidx.compose.material3.Scaffold] that owns (and
 * consumes) the top status-bar inset, so this bar is actually laid out *below* the status bar and the
 * accent [background] can never reach y=0 on its own — the status bar would otherwise stay the white
 * window background. So the status-bar plane is painted at the **window** level instead: while the bar
 * is composed [SelectionStatusBarChrome] tints the real status bar to [JGalleryColors.Accent] and flips
 * its icons to light content, restoring both on exit. The status bar and this 56dp bar are then the same
 * accent, flush and seamless — no white gap above, no colour bleed past the bar.
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
    SelectionStatusBarChrome()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JGalleryColors.Accent)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 4.dp)
            .testTag("selection_top_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("selection_close")) {
            Icon(Icons.Outlined.Close, contentDescription = "Exit selection", tint = JGalleryColors.OnAccent)
        }
        Text(
            text = "$count selected",
            color = JGalleryColors.OnAccent,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("selection_count"),
        )
        Text(
            text = if (allSelected) "Deselect all" else "Select all",
            color = JGalleryColors.OnAccent,
            fontWeight = FontWeight.SemiBold,
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
 * While composed, tints the window's status bar to match the accent selection top bar (design G1-D7
 * item 9 / G1-D8 item 5) — the shell's edge-to-edge [androidx.compose.material3.Scaffold] offsets the
 * bar below the status bar, so painting the plane behind the bar is done at the window level here:
 *  - the status-bar **background** is set to [JGalleryColors.Accent], so there's no white status bar
 *    and no seam between it and the 56dp accent bar directly beneath it (contrast enforcement is turned
 *    off so the opaque accent isn't scrimmed on 3-button navigation);
 *  - the status-bar **icons** flip to light (white) content so they stay legible over the accent.
 *
 * Both the prior status-bar colour and icon appearance are captured on enter and restored on exit, so
 * leaving selection mode reverts cleanly to the normal (transparent, dark-icon) treatment. A no-op in
 * Compose previews / non-Activity hosts. (targetSdk 34 → the legacy `statusBarColor` is still honoured.)
 */
@Composable
private fun SelectionStatusBarChrome() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val accent = JGalleryColors.Accent.toArgb()
    DisposableEffect(accent) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousColor = window?.statusBarColor
        val previousContrast = window?.isStatusBarContrastEnforced
        val previousLightIcons = controller?.isAppearanceLightStatusBars
        window?.isStatusBarContrastEnforced = false
        window?.statusBarColor = accent
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (window != null && previousColor != null) {
                window.statusBarColor = previousColor
            }
            if (window != null && previousContrast != null) {
                window.isStatusBarContrastEnforced = previousContrast
            }
            if (controller != null && previousLightIcons != null) {
                controller.isAppearanceLightStatusBars = previousLightIcons
            }
        }
    }
}

/**
 * Bottom bulk-action bar (spec §7.6): Copy to / Move to / Delete, each driving an E8 primitive via
 * [onAction]. Disabled while [enabled] is false (e.g. an op is already running). Shown only while a
 * selection exists.
 *
 * Design G1-D7 item 11: when [onDetails] is supplied the bar also carries a **Details** action, valid
 * for any selection ≥ 1 (no arity gate), so multi-select photos/videos can read their aggregate
 * properties — parity with the album selection bar's multi-safe Details.
 */
@Composable
fun BulkActionBar(
    enabled: Boolean,
    onAction: (BulkAction) -> Unit,
    modifier: Modifier = Modifier,
    onDetails: (() -> Unit)? = null,
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
        if (onDetails != null) {
            // Details is multi-safe: enabled for any selection (independent of a running op).
            BulkActionButton("Details", Icons.Outlined.Info, enabled = true, tag = "bulk_details") { onDetails() }
        }
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
