package com.appblish.jgallery.core.ui.selection

import android.net.Uri
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The shared brain for media multi-select + bulk operations (spec §7.6). Both the Photos time-grid
 * and the album-detail grid own one of these; the only thing that differs is the ordered key list
 * they feed [dragOver]/[selectAll]. Keeping it here (in `:core:ui`, decoupled from `:core:index` via
 * the injected E8 flow-producers) means the two grids share one implementation and one test.
 *
 * Everything is a [StateFlow] so hosting it in a ViewModel makes both the selection and a running
 * bulk op survive configuration changes — the op keeps streaming in the retained scope while the UI
 * re-subscribes (spec §7.6 "state survives config changes; off main thread").
 *
 * @param scope the ViewModel scope; bulk ops launch here so they outlive recomposition.
 * @param copy/[move]/[trash] the E8 primitives (usually `MediaOperationsRepository::copy`, etc.).
 */
class MediaSelectionController(
    private val scope: CoroutineScope,
    private val copy: (ids: List<MediaId>, destinationBucketId: String) -> Flow<FileOperationEvent>,
    private val move: (ids: List<MediaId>, destinationBucketId: String) -> Flow<FileOperationEvent>,
    private val trash: (ids: List<MediaId>) -> Flow<FileOperationEvent>,
    // Create-and-fill destinations for the D4-03 unified sheet: the selection becomes a brand-new
    // album's cover + contents (name-based §1.6 seam, APP-422), so there is no empty-album problem.
    // Default to an empty flow so existing tests that only exercise Copy/Move/Trash keep compiling.
    private val copyToNew: (ids: List<MediaId>, name: String) -> Flow<FileOperationEvent> = { _, _ -> emptyFlow() },
    private val moveToNew: (ids: List<MediaId>, name: String) -> Flow<FileOperationEvent> = { _, _ -> emptyFlow() },
    // "Save a copy" export into a user-picked SAF folder (G2 · APP-549). Defaults to an empty flow so
    // existing tests that only exercise Copy/Move/Trash keep compiling.
    private val export: (ids: List<MediaId>, treeUri: Uri) -> Flow<FileOperationEvent> = { _, _ -> emptyFlow() },
) {
    private val _selection = MutableStateFlow(SelectionState<MediaId>())
    val selection: StateFlow<SelectionState<MediaId>> = _selection.asStateFlow()

    private val _bulk = MutableStateFlow<BulkOperationUiState>(BulkOperationUiState.Idle)
    val bulk: StateFlow<BulkOperationUiState> = _bulk.asStateFlow()

    /** Selection snapshot captured at drag-start, so shrinking a drag deselects (see [SelectionState]). */
    private var dragBase: Set<MediaId> = emptySet()
    private var runningJob: Job? = null

    // --- Selection (spec §7.6: long-press enter, tap toggle, drag range, Select All) ---

    /** Tap toggles a single item (also used to seed selection on a plain tap in an active grid). */
    fun toggle(id: MediaId) = _selection.update { it.toggle(id) }

    /** Long-press / drag-start: enter selection on [id] and snapshot the base for the drag range. */
    fun beginDrag(id: MediaId) {
        dragBase = _selection.value.selected
        _selection.update { it.anchorOn(id) }
    }

    /** Drag moved over [id]: extend the selection to the anchor…id span within [ordered]. */
    fun dragOver(id: MediaId, ordered: List<MediaId>) =
        _selection.update { it.extendRangeTo(id, ordered, dragBase) }

    fun selectAll(all: Collection<MediaId>) = _selection.update { it.selectAll(all) }

    fun clearSelection() = _selection.update { it.clear() }

    // --- Bulk ops (spec §7.6: Copy to / Move to / Delete → E8, progress + summary) ---

    /**
     * Run [action] over the current selection off the main thread. [destinationBucketId] is required
     * for COPY/MOVE (the picked folder) and ignored for TRASH. No-ops on an empty selection. The
     * running flow is collected in [scope]; each `InProgress` updates the progress state and the
     * terminal `Completed` publishes the "X done, Y failed" summary and exits selection mode.
     */
    fun run(action: BulkAction, destinationBucketId: String? = null) {
        val ids = _selection.value.selected.toList()
        if (ids.isEmpty()) return
        if ((action == BulkAction.COPY || action == BulkAction.MOVE) && destinationBucketId == null) return

        val events: Flow<FileOperationEvent> = when (action) {
            BulkAction.COPY -> copy(ids, destinationBucketId!!)
            BulkAction.MOVE -> move(ids, destinationBucketId!!)
            BulkAction.TRASH -> trash(ids)
            BulkAction.EXPORT -> return // Export has its own entry point ([runExport], SAF tree uri).
        }
        launchOp(action, events)
    }

    /**
     * Create a new album named [name] and copy/move the current selection into it (spec §7.2, C1-03
     * inline "New album" step of the unified [MoveDestinationSheet]). The selected items become the
     * new album's cover + contents — no empty-album problem. No-ops on an empty selection, a blank
     * name, or TRASH (which has no create-new destination).
     */
    fun runToNewAlbum(action: BulkAction, name: String) {
        val ids = _selection.value.selected.toList()
        if (ids.isEmpty() || name.isBlank()) return
        val events: Flow<FileOperationEvent> = when (action) {
            BulkAction.COPY -> copyToNew(ids, name)
            BulkAction.MOVE -> moveToNew(ids, name)
            BulkAction.TRASH, BulkAction.EXPORT -> return // no create-new destination for these verbs
        }
        launchOp(action, events)
    }

    /**
     * "Save a copy" — export the current selection into the user-picked SAF folder [treeUri] (G2 ·
     * APP-549). Called by the host after `ACTION_OPEN_DOCUMENT_TREE` returns a grant. Streams through the
     * same progress → summary machinery as [run]; no-ops on an empty selection.
     */
    fun runExport(treeUri: Uri) {
        val ids = _selection.value.selected.toList()
        if (ids.isEmpty()) return
        launchOp(BulkAction.EXPORT, export(ids, treeUri))
    }

    /** Shared streaming collector for [run] / [runToNewAlbum]: progress → summary, then exit selection. */
    private fun launchOp(action: BulkAction, events: Flow<FileOperationEvent>) {
        runningJob?.cancel()
        _bulk.value = BulkOperationUiState.Running(action, progress = null)
        runningJob = scope.launch {
            events.collect { event ->
                when (event) {
                    is FileOperationEvent.InProgress ->
                        _bulk.value = BulkOperationUiState.Running(action, event.progress)
                    is FileOperationEvent.Completed -> {
                        _bulk.value = BulkOperationUiState.Finished(action, event.summary)
                        _selection.value = SelectionState()
                    }
                }
            }
        }
    }

    /** Cancel an in-flight op (spec: partial item rolls back in the engine); returns to Idle. */
    fun cancel() {
        runningJob?.cancel()
        runningJob = null
        _bulk.value = BulkOperationUiState.Idle
    }

    /** Dismiss the result summary. */
    fun dismissResult() {
        _bulk.value = BulkOperationUiState.Idle
    }
}
