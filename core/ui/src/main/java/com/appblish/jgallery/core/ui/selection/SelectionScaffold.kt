package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId

/**
 * The whole multi-select chrome for a media grid (spec §7.6), reused verbatim by the Photos tab and
 * album detail. It swaps the normal [tabHeader] for a [SelectionTopBar] while a selection exists,
 * hangs a [BulkActionBar] under the grid, and owns the destination-picker / delete-confirm /
 * progress / result overlays — so a screen only has to supply its grid and the controller callbacks.
 *
 * The [grid] content stays per-screen (the Photos time-grid vs. album detail's flat grid) but shares
 * the same selection state + gesture + tile-overlay helpers, so behaviour is identical in both.
 *
 * @param allIds every selectable id currently in the grid — powers Select All / all-selected.
 * @param sourceBucketId the album being viewed (null on the Photos tab); excluded from destinations.
 * @param onRun the controller's `run(action, destinationBucketId)`.
 */
@Composable
fun SelectionScaffold(
    selection: SelectionState<MediaId>,
    bulk: BulkOperationUiState,
    albums: List<Album>,
    allIds: List<MediaId>,
    tabHeader: @Composable () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRun: (action: BulkAction, destinationBucketId: String?) -> Unit,
    onCancel: () -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
    sourceBucketId: String? = null,
    grid: @Composable () -> Unit,
) {
    // Which action, if any, is waiting on a destination pick or a delete confirm.
    var pendingPick by remember { mutableStateOf<BulkAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // A large Select-All op paused on its at-scale warning (design W3-09) before the normal flow.
    var pendingLarge by remember { mutableStateOf<BulkAction?>(null) }

    // Normal routing once any at-scale warning has cleared: Copy/Move pick a destination, Trash confirms.
    val routeAction: (BulkAction) -> Unit = { action ->
        when (action) {
            BulkAction.COPY, BulkAction.MOVE -> pendingPick = action
            BulkAction.TRASH -> confirmDelete = true
        }
    }

    Column(modifier.fillMaxSize()) {
        if (selection.isActive) {
            SelectionTopBar(
                count = selection.count,
                allSelected = allIds.isNotEmpty() && selection.selected.containsAll(allIds),
                onClose = onClearSelection,
                onSelectAll = onSelectAll,
                onDeselectAll = onClearSelection,
            )
        } else {
            tabHeader()
        }

        Box(Modifier.weight(1f).fillMaxSize()) { grid() }

        if (selection.isActive) {
            BulkActionBar(
                enabled = bulk !is BulkOperationUiState.Running,
                onAction = { action ->
                    if (isLargeSelection(selection.count)) pendingLarge = action else routeAction(action)
                },
            )
        }
    }

    pendingLarge?.let { action ->
        LargeSelectionWarningDialog(
            count = selection.count,
            action = action,
            onConfirm = {
                pendingLarge = null
                routeAction(action)
            },
            onDismiss = { pendingLarge = null },
        )
    }

    pendingPick?.let { action ->
        DestinationPickerSheet(
            title = if (action == BulkAction.COPY) "Copy to" else "Move to",
            albums = albums,
            excludeBucketId = sourceBucketId,
            onPick = { bucketId ->
                pendingPick = null
                onRun(action, bucketId)
            },
            onDismiss = { pendingPick = null },
        )
    }

    if (confirmDelete) {
        BulkDeleteConfirmDialog(
            count = selection.count,
            onConfirm = {
                confirmDelete = false
                onRun(BulkAction.TRASH, null)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    when (bulk) {
        is BulkOperationUiState.Running -> BulkProgressDialog(bulk, onCancel = onCancel)
        is BulkOperationUiState.Finished -> BulkResultDialog(bulk, onDismiss = onDismissResult)
        BulkOperationUiState.Idle -> Unit
    }
}

/** No-op layout helper kept for symmetry with other core:ui scaffolds; not currently needed. */
private fun Modifier.passthrough(): Modifier = layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { p.place(0, 0) } }
