package com.appblish.jgallery.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.TrashRepository
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.TrashEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which long operation is currently running, so the screen can show the right busy label. */
enum class TrashOpKind { RESTORE, DELETE, EMPTY }

/** Terminal "X restored / deleted, Y failed" summary for the last completed operation. */
data class TrashOperationSummary(val kind: TrashOpKind, val result: OperationResult)

/** View state for the Recycle Bin (design W2-09). */
sealed interface TrashUiState {
    /** First read of the persisted bin manifest (usually instantaneous). */
    data object Loading : TrashUiState

    /** The bin is empty — mirror W1's empty-library language ("a door, not a wall"). */
    data object Empty : TrashUiState

    /** Populated bin; [selection] is non-empty only in multi-select mode. */
    data class Content(
        val entries: List<TrashEntry>,
        val selection: Set<MediaId> = emptySet(),
    ) : TrashUiState {
        val inSelectionMode: Boolean get() = selection.isNotEmpty()
        val allSelected: Boolean get() = selection.size == entries.size && entries.isNotEmpty()
    }
}

/**
 * Recycle Bin (spec §7.5). Observes the app-managed bin manifest through the §1.6 boundary
 * ([StorageAccess.observeTrash]) and drives restore / permanent-delete / empty — every mutation runs
 * off the main thread inside the storage engine and reports a result summary. The screen never
 * touches storage or a clock.
 *
 * On first composition it triggers a best-effort purge of items past the 30-day retention window, so
 * an opened bin never shows an item that should already be gone.
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trash: TrashRepository,
) : ViewModel() {

    private val selection = MutableStateFlow<Set<MediaId>>(emptySet())

    private val _operationInFlight = MutableStateFlow<TrashOpKind?>(null)
    /** Non-null while a restore/delete/empty is running (drives the "restoring…"/"emptying…" state). */
    val operationInFlight: StateFlow<TrashOpKind?> = _operationInFlight.asStateFlow()

    private val _lastSummary = MutableStateFlow<TrashOperationSummary?>(null)
    /** The most recent completed-operation summary, for a one-shot snackbar. Cleared via [consumeSummary]. */
    val lastSummary: StateFlow<TrashOperationSummary?> = _lastSummary.asStateFlow()

    val state: StateFlow<TrashUiState> =
        combine(trash.observeTrash(), selection) { entries, sel ->
            if (entries.isEmpty()) {
                TrashUiState.Empty
            } else {
                // A selected id that has since left the bin (restored/purged elsewhere) must not linger.
                val present = entries.mapTo(HashSet(entries.size)) { it.id }
                TrashUiState.Content(entries = entries, selection = sel.intersect(present))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState.Loading)

    init {
        viewModelScope.launch { runCatching { trash.purgeExpired() } }
    }

    // Pull-to-refresh (design G1-D7 item 13): the bin's re-scan is a re-evaluation of the 30-day
    // retention window — expired entries are purged and the manifest stream re-emits the pruned list.
    // Re-entrant pulls while one is in flight are ignored.
    private val refreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                runCatching { trash.purgeExpired() }
            } finally {
                refreshing.value = false
            }
        }
    }

    // --- selection ---

    fun toggleSelection(id: MediaId) {
        selection.update { if (id in it) it - id else it + id }
    }

    fun selectAll() {
        val content = state.value as? TrashUiState.Content ?: return
        selection.value = content.entries.mapTo(HashSet()) { it.id }
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    // --- operations (all off the main thread inside the storage engine) ---

    /** Restore the selected items to their original paths, then leave selection mode. */
    fun restoreSelected() = restore(selection.value.toList())

    /** Permanently delete the selected items (called only after the W2-08 step-2 confirm). */
    fun deleteSelected() = deletePermanent(selection.value.toList())

    /** Restore every item in the bin (bottom-bar "Restore all"). */
    fun restoreAll() {
        val content = state.value as? TrashUiState.Content ?: return
        restore(content.entries.map { it.id })
    }

    fun restore(ids: List<MediaId>) {
        if (ids.isEmpty()) return
        launchOperation(TrashOpKind.RESTORE) { trash.restore(ids) }
    }

    fun deletePermanent(ids: List<MediaId>) {
        if (ids.isEmpty()) return
        launchOperation(TrashOpKind.DELETE) { trash.deletePermanently(ids) }
    }

    /** Empty the whole bin (screen-level "Empty bin", after its guarded confirm). */
    fun emptyBin() {
        launchOperation(TrashOpKind.EMPTY) { trash.emptyTrash() }
    }

    fun consumeSummary() {
        _lastSummary.value = null
    }

    /**
     * Collect [operation] to its terminal [FileOperationEvent.Completed], publishing the busy state
     * and the result summary. Selection is cleared on completion so the bar collapses back to the
     * default bottom bar. The flow's own work runs on the storage IO dispatcher; only the terminal
     * summary crosses back here.
     */
    private fun launchOperation(kind: TrashOpKind, operation: () -> Flow<FileOperationEvent>) {
        if (_operationInFlight.value != null) return // ignore re-taps while one is running
        _operationInFlight.value = kind
        viewModelScope.launch {
            val result = runCatching {
                operation()
                    .filterIsInstance<FileOperationEvent.Completed>()
                    .firstOrNull()
                    ?.summary
            }.getOrNull()
            if (result != null) _lastSummary.value = TrashOperationSummary(kind, result)
            selection.value = emptySet()
            _operationInFlight.value = null
        }
    }
}
