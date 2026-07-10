package com.appblish.jgallery.feature.viewer

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.playback.PlaybackSources
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ViewerUiState {
    /** First index emission not in yet — the canvas stays black; in practice this is instant (cache). */
    data object Loading : ViewerUiState

    /** Nothing to show (album emptied underneath us, e.g. all items deleted externally). */
    data object Empty : ViewerUiState

    /**
     * [initialIndex] is resolved once, from the first emission containing the launch item, and then
     * frozen — later index updates must not yank the pager back to it.
     */
    data class Ready(val items: List<MediaItem>, val initialIndex: Int) : ViewerUiState
}

/**
 * Serves the viewer pager from the cached index (spec §1 rule 4 — same source of truth as the
 * grids, so pager order always matches the grid the user tapped). Args arrive via the route
 * (`viewer/{mediaId}?bucketId=`): the item to open on, and the album scope (null = Photos stream).
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    repository: MediaIndexRepository,
    private val operations: MediaOperationsRepository,
    savedStateHandle: SavedStateHandle,
    /** Boundary-routed Media3 sources, handed to the video pages (§1.6 — no uri ever reaches the UI). */
    val playback: PlaybackSources,
) : ViewModel() {

    private val initialId = MediaId(checkNotNull(savedStateHandle.get<String>(VIEWER_MEDIA_ID_ARG)))
    private val bucketId: String? = savedStateHandle.get<String>(VIEWER_BUCKET_ID_ARG)

    private var frozenInitialIndex: Int? = null

    val uiState: StateFlow<ViewerUiState> =
        repository.observeMedia(MediaQuery(bucketId = bucketId))
            .map { items ->
                if (items.isEmpty()) {
                    ViewerUiState.Empty
                } else {
                    ViewerUiState.Ready(items, initialIndex = resolveInitialIndex(items))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerUiState.Loading)

    /** Device albums offered by the Copy to / Move to destination picker (spec §7.1/§7.2). */
    val destinations: StateFlow<List<Album>> =
        repository.observeAlbums()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _action = MutableStateFlow<ViewerActionUiState>(ViewerActionUiState.Idle)
    internal val action: StateFlow<ViewerActionUiState> = _action.asStateFlow()

    /** One-shot: a `content://` uri to fire "Set as" against (spec §7.4); consumed by the screen. */
    private val setAsRequests = Channel<Uri>(Channel.BUFFERED)
    val setAsUri: Flow<Uri> = setAsRequests.receiveAsFlow()

    // Ops run in the retained viewModelScope so a copy/move survives a config change (spec §7.6).
    private var runningJob: Job? = null

    fun copyTo(id: MediaId, destinationBucketId: String) =
        runStreamingOp(ViewerActionKind.COPY) { operations.copy(listOf(id), destinationBucketId) }

    fun moveTo(id: MediaId, destinationBucketId: String) =
        runStreamingOp(ViewerActionKind.MOVE) { operations.move(listOf(id), destinationBucketId) }

    fun delete(id: MediaId) =
        runStreamingOp(ViewerActionKind.TRASH) { operations.moveToTrash(listOf(id)) }

    fun rename(id: MediaId, newDisplayName: String) {
        runningJob?.cancel()
        _action.value = ViewerActionUiState.Running(ViewerActionKind.RENAME)
        runningJob = viewModelScope.launch {
            _action.value = ViewerActionUiState.Finished(
                ViewerActionKind.RENAME,
                operations.rename(id, newDisplayName),
            )
        }
    }

    /** Resolve the boundary uri and emit it for the screen to launch, or report the item is gone. */
    fun setAs(id: MediaId) {
        viewModelScope.launch {
            val uri = operations.viewUri(id)
            if (uri != null) {
                setAsRequests.send(uri)
            } else {
                _action.value = ViewerActionUiState.Finished(
                    ViewerActionKind.SET_AS,
                    OperationResult(
                        succeeded = 0,
                        failed = 1,
                        failures = listOf(OperationResult.Failure(id, "item no longer available")),
                    ),
                )
            }
        }
    }

    fun dismissActionResult() {
        _action.value = ViewerActionUiState.Idle
    }

    private fun runStreamingOp(kind: ViewerActionKind, op: () -> Flow<FileOperationEvent>) {
        runningJob?.cancel()
        _action.value = ViewerActionUiState.Running(kind)
        runningJob = viewModelScope.launch {
            var summary: OperationResult? = null
            op().collect { event ->
                if (event is FileOperationEvent.Completed) summary = event.summary
            }
            _action.value = ViewerActionUiState.Finished(
                kind,
                summary ?: OperationResult(
                    succeeded = 0,
                    failed = 1,
                    failures = listOf(OperationResult.Failure(MediaId(""), "operation did not complete")),
                ),
            )
        }
    }

    private fun resolveInitialIndex(items: List<MediaItem>): Int =
        frozenInitialIndex
            ?: items.indexOfFirst { it.id == initialId }
                .coerceAtLeast(0) // launch item already gone → open on the first item
                .also { frozenInitialIndex = it }
}
