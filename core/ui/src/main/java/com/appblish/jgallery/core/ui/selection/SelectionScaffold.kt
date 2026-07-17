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
 * @param coverFor supplies each album's cover model for the [MoveDestinationSheet] thumbnail grid
 *   (feature passes `{ it.coverRequest() }` so `:core:ui` never depends on `:core:thumbs`, §1.6).
 * @param onCreateNew create a fresh album by name and copy/move the selection into it (D4-03 unify).
 * @param onBrowseFolders fall back to the device-folder picker (W2-04, not built yet — keep honest).
 * @param details aggregate properties of the current selection (design G1-D7 item 11); when non-null a
 *   multi-safe **Details** action is added to the bulk bar and opens a read-only summary dialog.
 * @param onRename single-only **Rename** action (design G1-D8 item 1); when non-null it is added to the
 *   bulk bar's shared ⋮ overflow, enabled only when exactly one item is selected. The host owns the
 *   rename dialog + operation (parity with the album selection bar's Rename).
 * @param onShare multi-safe **Share** action (G2 · APP-541); when non-null a Share entry is added to the
 *   bulk bar's shared ⋮ overflow (valid for any selection ≥ 1) and the host resolves the selection to
 *   §1.6 content uris and fires the system share sheet.
 * @param onExport multi-safe **Save a copy** action (G2 · APP-549); when non-null a "Save a copy" entry
 *   is added to the shared ⋮ overflow (valid for any selection ≥ 1). The host launches the SAF folder
 *   picker (`ACTION_OPEN_DOCUMENT_TREE`) and streams the selection into the granted tree.
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
    coverFor: (Album) -> Any? = { null },
    onCreateNew: (action: BulkAction, name: String) -> Unit = { _, _ -> },
    onBrowseFolders: () -> Unit = {},
    details: SelectionDetails? = null,
    onRename: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    grid: @Composable () -> Unit,
) {
    // Which action, if any, is waiting on a destination pick or a delete confirm.
    var pendingPick by remember { mutableStateOf<BulkAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    // A large Select-All op paused on its at-scale warning (design W3-09) before the normal flow.
    var pendingLarge by remember { mutableStateOf<BulkAction?>(null) }

    // Normal routing once any at-scale warning has cleared: Copy/Move pick a destination, Trash confirms.
    val routeAction: (BulkAction) -> Unit = { action ->
        when (action) {
            BulkAction.COPY, BulkAction.MOVE -> pendingPick = action
            BulkAction.TRASH -> confirmDelete = true
            // Export is launched from the ⋮ overflow ([onExport] → SAF picker), never the main bar.
            BulkAction.EXPORT -> Unit
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
                onDetails = details?.let { { showDetails = true } },
                // Shared ⋮ overflow: multi-safe Share (G2 · APP-541) + Save a copy (G2 · APP-549) first,
                // then single-only Rename (G1-D8 item 1, enabled only when exactly one item is selected).
                overflowActions = buildList {
                    if (onShare != null) add(SelectionAction.SHARE)
                    if (onExport != null) add(SelectionAction.EXPORT)
                    if (onRename != null) add(SelectionAction.RENAME)
                },
                selectionCount = selection.count,
                onOverflowAction = { action ->
                    when (action) {
                        SelectionAction.SHARE -> onShare?.invoke()
                        SelectionAction.EXPORT -> onExport?.invoke()
                        SelectionAction.RENAME -> onRename?.invoke()
                        else -> Unit
                    }
                },
            )
        }
    }

    if (showDetails && details != null) {
        SelectionDetailsDialog(details = details, onDismiss = { showDetails = false })
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
        // D4-03: one cover-thumbnail sheet for every Copy/Move (viewer already used it; now the bulk
        // selection path does too), with the inline "New album" create-and-move step. The text-row
        // DestinationPickerSheet is retired from this path.
        MoveDestinationSheet(
            verb = if (action == BulkAction.COPY) AlbumOpVerb.COPY else AlbumOpVerb.MOVE,
            itemCount = selection.count,
            albums = albums,
            coverFor = coverFor,
            excludeBucketId = sourceBucketId,
            onPick = { bucketId ->
                pendingPick = null
                onRun(action, bucketId)
            },
            onCreateNew = { name ->
                pendingPick = null
                onCreateNew(action, name)
            },
            onBrowseFolders = {
                pendingPick = null
                onBrowseFolders()
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
