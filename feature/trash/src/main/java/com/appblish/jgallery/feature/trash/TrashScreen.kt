package com.appblish.jgallery.feature.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.TrashEntry
import com.appblish.jgallery.core.model.TrashPolicy
import com.appblish.jgallery.core.ui.grid.GalleryPullToRefresh
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.GridReflowPlacementSpec
import com.appblish.jgallery.core.ui.grid.ScrollToTopFab
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.grid.rememberGridZoomState
import com.appblish.jgallery.core.thumbs.ThumbnailRequest
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * Recycle Bin screen (spec §7.5, design W2-09). Holds deleted media safely for 30 days and lets the
 * user restore it to where it was or clear it for good. All state comes from [TrashViewModel]; every
 * mutation is routed through the §1.6 storage boundary off the main thread.
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inFlight by viewModel.operationInFlight.collectAsStateWithLifecycle()
    val summary by viewModel.lastSummary.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    TrashScreen(
        state = state,
        operationInFlight = inFlight,
        summary = summary,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onBack = onBack,
        onToggleSelect = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onRestoreSelected = viewModel::restoreSelected,
        onDeleteSelected = viewModel::deleteSelected,
        onRestoreAll = viewModel::restoreAll,
        onEmptyBin = viewModel::emptyBin,
        onSummaryShown = viewModel::consumeSummary,
        modifier = modifier,
    )
}

/**
 * Stateless body — instrumented/screenshot tests drive this directly (fixture bin, fixed [now] so the
 * days-left badges are deterministic) without Hilt or a real storage backend.
 */
@Composable
fun TrashScreen(
    state: TrashUiState,
    operationInFlight: TrashOpKind?,
    summary: TrashOperationSummary?,
    onBack: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onToggleSelect: (MediaId) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRestoreSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyBin: () -> Unit,
    onSummaryShown: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val selectionMode = (state as? TrashUiState.Content)?.inSelectionMode == true
    // In selection mode, the system back gesture collapses the selection rather than leaving the bin.
    BackHandler(enabled = selectionMode) { onClearSelection() }

    var showEmptyBinDialog by remember { mutableStateOf(false) }
    var showPermanentDeleteDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(summary) {
        summary?.let {
            snackbarHostState.showSnackbar(summaryMessage(it))
            onSummaryShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("trash_screen"),
        containerColor = JGalleryColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state is TrashUiState.Content && state.inSelectionMode) {
                TrashSelectionBar(
                    count = state.selection.size,
                    allSelected = state.allSelected,
                    onClose = onClearSelection,
                    onSelectAll = onSelectAll,
                )
            } else {
                TrashTopBar(
                    showOverflow = state is TrashUiState.Content,
                    onEmptyBin = { showEmptyBinDialog = true },
                    onSelect = { /* long-press a tile to select; overflow mirrors it via first item */ },
                )
            }
        },
        bottomBar = {
            when {
                state !is TrashUiState.Content -> Unit
                state.inSelectionMode -> TrashSelectionActionsBar(
                    onRestore = onRestoreSelected,
                    onDelete = { showPermanentDeleteDialog = true },
                )
                else -> TrashDefaultActionsBar(
                    onRestoreAll = onRestoreAll,
                    onEmptyBin = { showEmptyBinDialog = true },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                TrashUiState.Loading -> Unit // bin manifest reads instantly; no long spinner needed
                TrashUiState.Empty -> EmptyTabState(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Recycle Bin is empty",
                    caption = "Deleted photos and videos wait here for 30 days before they’re removed " +
                        "for good. Nothing to restore right now.",
                )
                is TrashUiState.Content ->
                    // Pull-to-refresh (design G1-D7 item 13): shared wrapper, identical to the other grids;
                    // on the bin a pull re-evaluates the 30-day retention window.
                    GalleryPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
                        TrashGrid(
                            entries = state.entries,
                            selection = state.selection,
                            selectionMode = state.inSelectionMode,
                            now = now,
                            onToggleSelect = onToggleSelect,
                        )
                    }
            }

            if (operationInFlight != null) {
                Text(
                    text = when (operationInFlight) {
                        TrashOpKind.RESTORE -> "Restoring…"
                        TrashOpKind.DELETE -> "Deleting…"
                        TrashOpKind.EMPTY -> "Emptying bin…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = JGalleryColors.OnAccent,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(JGalleryColors.Text)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .testTag("trash_busy"),
                )
            }
        }
    }

    if (showEmptyBinDialog) {
        ConfirmDialog(
            testTag = "empty_bin_dialog",
            title = "Empty Recycle Bin?",
            body = "Everything in the bin will be permanently deleted. This can’t be undone — not " +
                "even from the Recycle Bin.",
            confirmLabel = "Empty bin",
            onConfirm = {
                showEmptyBinDialog = false
                onEmptyBin()
            },
            onDismiss = { showEmptyBinDialog = false },
        )
    }

    if (showPermanentDeleteDialog) {
        val count = (state as? TrashUiState.Content)?.selection?.size ?: 0
        // W2-08 step 2: permanent delete is NEVER one tap — this second confirm gates it.
        ConfirmDialog(
            testTag = "permanent_delete_dialog",
            title = if (count == 1) "Delete permanently?" else "Delete $count items permanently?",
            body = "This removes the ${if (count == 1) "item" else "items"} from your device for good. " +
                "It can’t be restored — not even from the Recycle Bin.",
            confirmLabel = "Delete",
            onConfirm = {
                showPermanentDeleteDialog = false
                onDeleteSelected()
            },
            onDismiss = { showPermanentDeleteDialog = false },
        )
    }
}

// --- app bars ---

@Composable
private fun TrashTopBar(
    showOverflow: Boolean,
    onEmptyBin: () -> Unit,
    onSelect: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Recycle Bin",
                style = MaterialTheme.typography.displaySmall,
                color = JGalleryColors.Text,
                modifier = Modifier.weight(1f),
            )
            if (showOverflow) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag("trash_overflow"),
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = JGalleryColors.Text)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Empty bin") },
                            onClick = { menuOpen = false; onEmptyBin() },
                            modifier = Modifier.testTag("trash_overflow_empty"),
                        )
                        DropdownMenuItem(
                            text = { Text("Select") },
                            onClick = { menuOpen = false; onSelect() },
                        )
                    }
                }
            }
        }
        Text(
            text = "Items are deleted automatically after 30 days",
            style = MaterialTheme.typography.bodyMedium,
            color = JGalleryColors.TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun TrashSelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("trash_selection_close")) {
            Icon(Icons.Filled.Close, contentDescription = "Close selection", tint = JGalleryColors.Text)
        }
        Text(
            text = "$count selected",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = JGalleryColors.Text,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        TextButton(onClick = onSelectAll, modifier = Modifier.testTag("trash_select_all")) {
            Text(if (allSelected) "Deselect all" else "Select all", color = JGalleryColors.Accent)
        }
    }
}

// --- bottom action bars ---

@Composable
private fun TrashDefaultActionsBar(onRestoreAll: () -> Unit, onEmptyBin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BarAction(
            icon = Icons.Outlined.RestoreFromTrash,
            label = "Restore all",
            tint = JGalleryColors.Text,
            testTag = "trash_restore_all",
            onClick = onRestoreAll,
        )
        BarAction(
            icon = Icons.Filled.Delete,
            label = "Empty bin",
            tint = JGalleryColors.Destructive,
            testTag = "trash_empty_bin",
            onClick = onEmptyBin,
        )
    }
}

@Composable
private fun TrashSelectionActionsBar(onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BarAction(
            icon = Icons.Outlined.RestoreFromTrash,
            label = "Restore",
            tint = JGalleryColors.Accent,
            testTag = "trash_selection_restore",
            onClick = onRestore,
        )
        BarAction(
            icon = Icons.Filled.Delete,
            label = "Delete",
            tint = JGalleryColors.Destructive,
            testTag = "trash_selection_delete",
            onClick = onDelete,
        )
    }
}

@Composable
private fun BarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.padding(top = 4.dp))
    }
}

// --- grid ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashGrid(
    entries: List<TrashEntry>,
    selection: Set<MediaId>,
    selectionMode: Boolean,
    now: Long,
    onToggleSelect: (MediaId) -> Unit,
) {
    val zoom = rememberGridZoomState(initialColumns = ColumnCount(3))
    // APP-466: the shared grid set on the bin too — pinch-zoom columns, back-to-top FAB, and the
    // flat-grid fast-scroller (position bubble). The FAB yields the corner while a selection is active.
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(zoom.columns.value),
            state = zoom.gridState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                .gridPinchColumns(zoom).testTag("trash_grid"),
        ) {
            items(items = entries, key = { it.id.value }) { entry ->
                TrashTile(
                    // Pinch-release column swap slides each tile to its new slot (APP-519).
                    modifier = Modifier.animateItem(placementSpec = GridReflowPlacementSpec),
                    entry = entry,
                    selected = entry.id in selection,
                    daysLeft = TrashPolicy.daysLeft(entry.trashedAtMillis, now),
                    expiringSoon = TrashPolicy.isExpiringSoon(entry.trashedAtMillis, now),
                    onClick = { onToggleSelect(entry.id) },
                    onLongClick = { if (!selectionMode) onToggleSelect(entry.id) },
                )
            }
        }

        GridFastScroller(gridState = zoom.gridState, itemCount = entries.size)
        ScrollToTopFab(gridState = zoom.gridState, enabled = !selectionMode)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashTile(
    entry: TrashEntry,
    selected: Boolean,
    daysLeft: Int,
    expiringSoon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(JGalleryColors.TilePlaceholder)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("trash_tile_${entry.id.value}"),
    ) {
        AsyncImage(
            model = ThumbnailRequest(entry.id, entry.trashedAtMillis),
            contentDescription = entry.displayName,
            contentScale = ContentScale.Crop,
            // Trashed thumbnails read as "removed" — slightly muted (design W2-09 note 2).
            modifier = Modifier.fillMaxSize().alpha(if (selected) 0.6f else 0.85f),
        )

        // Days-left badge (bottom-left); amber when ≤5 days remain, else neutral dark chip.
        Text(
            text = "${daysLeft}d left",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (expiringSoon) JGalleryColors.Warn else Color(0xCC000000))
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("trash_days_left"),
        )

        if (entry.type == MediaType.VIDEO) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(JGalleryColors.Accent)
                    .padding(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = JGalleryColors.OnAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// --- dialogs ---

@Composable
private fun ConfirmDialog(
    testTag: String,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(testTag),
        icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = JGalleryColors.Destructive) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("${testTag}_confirm")) {
                Text(confirmLabel, color = JGalleryColors.Destructive, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = JGalleryColors.TextSecondary) }
        },
    )
}

private fun summaryMessage(summary: TrashOperationSummary): String {
    val r = summary.result
    val verb = when (summary.kind) {
        TrashOpKind.RESTORE -> "restored"
        TrashOpKind.DELETE, TrashOpKind.EMPTY -> "deleted"
    }
    return if (r.failed == 0) {
        "${r.succeeded} $verb"
    } else {
        "${r.succeeded} $verb, ${r.failed} failed"
    }
}
