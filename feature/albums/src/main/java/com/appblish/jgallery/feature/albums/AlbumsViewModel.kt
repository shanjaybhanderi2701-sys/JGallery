package com.appblish.jgallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.OperationProgress
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.ui.selection.AlbumOpUiState
import com.appblish.jgallery.core.ui.selection.AlbumOpVerb
import com.appblish.jgallery.core.ui.selection.AlbumOperationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** View state for the Albums tab. [Loading] is only ever the FIRST index run (design a13). */
sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data object Empty : AlbumsUiState
    data class Content(val albums: List<Album>) : AlbumsUiState
}

/**
 * The ordered Albums tab plus the per-bucket format presence that powers the C1-06 filter row, carried
 * together so the (expensive) catalog assembly runs only on index/pref changes — switching the format
 * chip just re-filters this cached data.
 */
private data class AlbumsCatalogData(
    val albums: List<Album>,
    val bucketFormats: Map<String, Set<MediaFilter>>,
    val libraryEmpty: Boolean,
)

/**
 * Albums tab (spec §3, design a04): device folders with cover + count, served straight from the
 * cached index. The active [SortSpec] (spec §6) re-orders the already-loaded album list in memory —
 * no re-scan — and both sort and column density persist per tab. Create-album (spec §6) goes through
 * the §1.6 [MediaOperationsRepository], so this feature never links `:core:storage`.
 */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    repository: MediaIndexRepository,
    private val operations: MediaOperationsRepository,
    private val preferences: AlbumsPreferences,
) : ViewModel() {

    private val createAlbumResults = Channel<CreateAlbumResult>(Channel.BUFFERED)

    /** One-shot create-album outcomes for the UI to surface as a snackbar/toast. */
    val createAlbumEvents: Flow<CreateAlbumResult> = createAlbumResults.receiveAsFlow()

    private val albumActionResults = Channel<AlbumActionResult>(Channel.BUFFERED)

    /** One-shot rename/delete-album outcomes for the UI to surface as a snackbar/toast. */
    val albumActionEvents: Flow<AlbumActionResult> = albumActionResults.receiveAsFlow()

    // A whole-album Copy/Move (item 13, C1-04) streams into the determinate [AlbumOpProgressDialog]
    // instead of a fire-and-forget toast: the op runs in the retained scope so it survives config
    // changes, and this state re-attaches the dialog to the still-running job. Null = no dialog.
    private val _albumOp = MutableStateFlow<AlbumOpUiState?>(null)
    val albumOp: StateFlow<AlbumOpUiState?> = _albumOp.asStateFlow()
    private var albumOpJob: Job? = null

    /**
     * Copy/Move destinations (spec §7.1/§7.2) — the current album list, straight from the index. The
     * picker omits the album being acted on so it can't target itself.
     */
    val destinations: StateFlow<List<Album>> =
        repository.observeAlbums()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The top-bar format filter (design C1-06), shared mental model with the Photos tab. */
    private val filterState = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = filterState.asStateFlow()

    /**
     * The Albums tab (spec C4) plus per-bucket format presence for the filter row. Assembled by
     * [AlbumsCatalog] from cache-backed inputs: the device folders, the whole media set (for the Video
     * smart album + the C1-06 filter's format presence), and the persisted pin/sort preferences.
     * Recent/Video are synthesized on top and the whole list is ordered deterministically
     * (pinned → Recent → Camera → Screenshots → Video → other folders by sort).
     */
    private val catalog: Flow<AlbumsCatalogData> =
        combine(
            repository.observeAlbums(),
            repository.observeMedia(MediaQuery()),
            preferences.pinnedBucketIds,
            preferences.sort,
        ) { albums, media, pinned, sort ->
            if (albums.isEmpty()) {
                AlbumsCatalogData(emptyList(), emptyMap(), libraryEmpty = true)
            } else {
                val videos = media.filter { it.type == MediaType.VIDEO }
                AlbumsCatalogData(
                    albums = AlbumsCatalog.buildAlbumsTab(albums, videos, pinned, sort),
                    bucketFormats = AlbumsCatalog.bucketFormats(media),
                    libraryEmpty = false,
                )
            }
        }

    val state: StateFlow<AlbumsUiState> =
        combine(catalog, filterState) { data, filter ->
            if (data.libraryEmpty) {
                AlbumsUiState.Empty
            } else {
                AlbumsUiState.Content(
                    AlbumsCatalog.applyFormatFilter(data.albums, filter, data.bucketFormats),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsUiState.Loading)

    fun setFilter(filter: MediaFilter) {
        filterState.value = filter
    }

    val columns: StateFlow<ColumnCount> =
        preferences.columns
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColumnCount.DEFAULT)

    val sort: StateFlow<SortSpec> =
        preferences.sort
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SortSpec())

    fun setColumns(columns: ColumnCount) {
        viewModelScope.launch { preferences.setColumns(columns) }
    }

    fun setSort(sort: SortSpec) {
        viewModelScope.launch { preferences.setSort(sort) }
    }

    /** Pin/unpin an album (spec C4 item 6). Persisted; pinned albums sort above the priority folders. */
    fun togglePin(album: Album) {
        viewModelScope.launch { preferences.setPinned(album.bucketId, !album.pinned) }
    }

    /** Create a new album folder (spec §6). Result is delivered on [createAlbumEvents]. */
    fun createAlbum(name: String) {
        viewModelScope.launch {
            val result = operations.createAlbum(name)
            createAlbumResults.send(
                if (result.succeeded > 0) {
                    CreateAlbumResult.Success(name.trim())
                } else {
                    CreateAlbumResult.Failure(result.failures.firstOrNull()?.reason ?: "Couldn't create album")
                },
            )
        }
    }

    /** Rename an album/folder as an entity (spec §7.3, §11). Result on [albumActionEvents]. */
    fun renameAlbum(album: Album, newName: String) {
        viewModelScope.launch {
            report(operations.renameAlbum(album.bucketId, newName), "Album renamed", "Couldn't rename album")
        }
    }

    /** Copy a whole album into [destinationBucketId] (spec §7.1). Streams into [albumOp]. */
    fun copyAlbum(album: Album, destinationBucketId: String) {
        runAlbumOp(AlbumOpVerb.COPY, album, destinationBucketId) {
            operations.copyAlbum(album.bucketId, destinationBucketId)
        }
    }

    /** Move a whole album into [destinationBucketId] (spec §7.2). Streams into [albumOp]. */
    fun moveAlbum(album: Album, destinationBucketId: String) {
        runAlbumOp(AlbumOpVerb.MOVE, album, destinationBucketId) {
            operations.moveAlbum(album.bucketId, destinationBucketId)
        }
    }

    /**
     * Drive a whole-album Copy/Move through the determinate progress dialog (item 13, C1-04): open on
     * [AlbumOpUiState.Running] (indeterminate until the first item lands), fold every `InProgress`
     * event into a live fraction, and resolve to [AlbumOpUiState.Finished] with the terminal summary.
     * A Cancel cancels the collecting job — the E8 engine then stops after the in-flight item with no
     * `Completed` event (§7.2) — so [Job.invokeOnCompletion] synthesises the "moved N before cancel"
     * summary from the last progress. Guarded by job identity so a superseding op never gets clobbered
     * by a stale completion handler.
     */
    private fun runAlbumOp(
        verb: AlbumOpVerb,
        album: Album,
        destinationBucketId: String,
        op: () -> Flow<FileOperationEvent>,
    ) {
        albumOpJob?.cancel()
        val destinationLabel = destinations.value.firstOrNull { it.bucketId == destinationBucketId }?.name
            ?: "destination"
        val context = AlbumOperationContext(
            verb = verb,
            albumName = album.name,
            destinationLabel = destinationLabel,
            total = album.itemCount,
        )
        _albumOp.value = AlbumOpUiState.Running(context, progress = null)

        var lastProgress: OperationProgress? = null
        var completed: OperationResult? = null
        val job = viewModelScope.launch {
            op().collect { event ->
                when (event) {
                    is FileOperationEvent.InProgress -> {
                        lastProgress = event.progress
                        val cancelling = (_albumOp.value as? AlbumOpUiState.Running)?.cancelling ?: false
                        _albumOp.value = AlbumOpUiState.Running(context, event.progress, cancelling)
                    }

                    is FileOperationEvent.Completed -> completed = event.summary
                }
            }
        }
        albumOpJob = job
        job.invokeOnCompletion { cause ->
            if (albumOpJob !== job) return@invokeOnCompletion // superseded by a newer op
            val cancelled = completed == null && cause is CancellationException
            val summary = completed
                ?: OperationResult(succeeded = lastProgress?.completed ?: 0, failed = 0)
            _albumOp.value = AlbumOpUiState.Finished(context, summary, cancelled = cancelled)
        }
    }

    /** Cooperatively cancel the running album op — the flip to "Cancelling…" then a synthesised summary. */
    fun cancelAlbumOp() {
        val running = _albumOp.value as? AlbumOpUiState.Running ?: return
        _albumOp.value = running.copy(cancelling = true)
        albumOpJob?.cancel()
    }

    /** Dismiss the terminal summary — closes the dialog. */
    fun dismissAlbumOp() {
        albumOpJob = null
        _albumOp.value = null
    }

    /** Move a whole album to the restorable Trash — "delete album" (spec §7.5, §11). */
    fun deleteAlbum(album: Album) {
        viewModelScope.launch {
            report(operations.deleteAlbum(album.bucketId).summary(), "Album moved to Trash", "Couldn't delete album")
        }
    }

    /** Fold a bulk op's event stream to its terminal summary (empty album → nothing done). */
    private suspend fun Flow<FileOperationEvent>.summary(): OperationResult =
        when (val terminal = last()) {
            is FileOperationEvent.Completed -> terminal.summary
            else -> OperationResult(succeeded = 0, failed = 0)
        }

    private suspend fun report(result: OperationResult, success: String, failureFallback: String) {
        albumActionResults.send(
            if (result.failed == 0 && result.succeeded > 0) {
                AlbumActionResult.Success(success)
            } else {
                AlbumActionResult.Failure(result.failures.firstOrNull()?.reason ?: failureFallback)
            },
        )
    }
}

/** Outcome of a rename/copy/move/delete-album request, surfaced to the UI as a message (spec §7). */
sealed interface AlbumActionResult {
    data class Success(val message: String) : AlbumActionResult
    data class Failure(val reason: String) : AlbumActionResult
}

/** Outcome of a create-album request, surfaced to the UI as a message (spec §6). */
sealed interface CreateAlbumResult {
    data class Success(val name: String) : CreateAlbumResult
    data class Failure(val reason: String) : CreateAlbumResult
}

/**
 * Order the album *list* by the active [SortSpec] (spec §6). Albums carry no file path, and their
 * "size" is naturally the item count, so the four keys map as: File Name → name, File Size → item
 * count, Last Modified → newest contained item, File Path → name (fallback, keeping the sort total
 * and stable rather than throwing — mirrors the media comparator). The default (Last Modified,
 * Descending) reproduces the index's own newest-first aggregate order.
 */
internal fun SortSpec.albumComparator(): Comparator<Album> {
    val ascending: Comparator<Album> = when (key) {
        SortKey.FILE_NAME, SortKey.FILE_PATH -> compareBy { it.name.lowercase() }
        SortKey.FILE_SIZE -> compareBy { it.itemCount }
        SortKey.LAST_MODIFIED -> compareBy { it.newestItemMillis }
    }
    return if (direction == SortDirection.DESCENDING) ascending.reversed() else ascending
}
