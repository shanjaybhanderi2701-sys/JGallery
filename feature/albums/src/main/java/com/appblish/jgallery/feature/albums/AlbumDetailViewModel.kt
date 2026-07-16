package com.appblish.jgallery.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.AlbumCapture
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.CaptureKind
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.filteredBy
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.ui.selection.BulkAction
import com.appblish.jgallery.core.ui.selection.MediaSelectionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** View state for one album's media grid. */
sealed interface AlbumDetailUiState {
    data object Loading : AlbumDetailUiState
    data object Empty : AlbumDetailUiState
    data class Content(val items: List<MediaItem>) : AlbumDetailUiState
}

/**
 * Album detail (spec §3): a flat media grid scoped to one bucket, served from the cached index the
 * same way the Photos tab is. It exists so E11 multi-select + bulk ops (spec §7.6) work in the
 * **Albums** surface too — the whole selection/bulk machinery is the shared [MediaSelectionController]
 * and [com.appblish.jgallery.core.ui.selection.SelectionScaffold], identical to Photos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaIndexRepository,
    private val operations: MediaOperationsRepository,
    private val viewPreferences: AlbumViewPreferences,
) : ViewModel() {

    /** The bucket being viewed; excluded from Copy/Move destinations so you can't target the source. */
    val bucketId: String = checkNotNull(savedStateHandle[ALBUM_DETAIL_BUCKET_ID_ARG]) {
        "AlbumDetail requires a bucketId argument"
    }
    val title: String = savedStateHandle.get<String>(ALBUM_DETAIL_NAME_ARG) ?: "Album"
    private val videoOnly: Boolean = savedStateHandle[ALBUM_DETAIL_VIDEO_ONLY_ARG] ?: false

    /**
     * The active top-bar format filter carried in from the tapped album card (design C1-06, APP-467):
     * opening a folder while "Videos"/"Photos"/"GIFs" is selected shows only that media, so the Albums
     * surface "yields only matching media" like the Photos tab. Defaults to [MediaFilter.ALL]. GIF/Photo
     * can't be expressed in the MediaStore query ([MediaType] is IMAGE|VIDEO only), so this filters the
     * cached items in memory — no rescan.
     */
    private val filter: MediaFilter =
        (savedStateHandle.get<String>(ALBUM_DETAIL_FILTER_ARG))
            ?.let { runCatching { MediaFilter.valueOf(it) }.getOrNull() }
            ?: MediaFilter.ALL

    /**
     * Translate the nav args into a cache query. Real folders scope by [bucketId]; the smart-album
     * sentinels (spec C4) map to library-wide queries — Recent = whole library newest-first,
     * All-Videos = every video. [videoOnly] narrows a real folder to its videos (Video → folder-wise).
     */
    private val baseQuery: MediaQuery = when (bucketId) {
        AlbumsCatalog.RECENT_BUCKET_ID ->
            MediaQuery(sort = SortSpec(SortKey.LAST_MODIFIED, SortDirection.DESCENDING))
        AlbumsCatalog.ALL_VIDEOS_BUCKET_ID ->
            MediaQuery(
                types = setOf(MediaType.VIDEO),
                sort = SortSpec(SortKey.LAST_MODIFIED, SortDirection.DESCENDING),
            )
        else -> MediaQuery(
            bucketId = bucketId,
            types = if (videoOnly) setOf(MediaType.VIDEO) else setOf(MediaType.IMAGE, MediaType.VIDEO),
        )
    }

    /**
     * Effective in-album view settings (G1-9, design APP-465 TB-03): the persisted per-album Sort +
     * Grid size, resolved through the album's scope (this album only vs all). Drives both the grid
     * ordering (via the cached-index [SortSpec]) and the column count.
     */
    val viewSettings: StateFlow<AlbumViewSettings> =
        viewPreferences.settings(bucketId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumViewSettings())

    /**
     * Re-sorting is a cheap re-order of the already-loaded cached rows (spec §6, no rescan/IO): a new
     * persisted [SortSpec] just re-issues the query with a different sort. Only the sort re-triggers
     * the query; a column-count change is UI-only, so it is filtered out here via [distinctUntilChanged].
     */
    val state: StateFlow<AlbumDetailUiState> =
        viewSettings
            .map { it.sort }
            .distinctUntilChanged()
            .flatMapLatest { sort -> repository.observeMedia(baseQuery.copy(sort = sort)) }
            .map { items ->
                val filtered = items.filteredBy(filter)
                if (filtered.isEmpty()) AlbumDetailUiState.Empty else AlbumDetailUiState.Content(filtered)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumDetailUiState.Loading)

    val selectionController = MediaSelectionController(
        scope = viewModelScope,
        copy = operations::copy,
        move = operations::move,
        trash = operations::moveToTrash,
        copyToNew = operations::copyToNewAlbum,
        moveToNew = operations::moveToNewAlbum,
    )
    val selection get() = selectionController.selection
    val bulk get() = selectionController.bulk

    val destinations: StateFlow<List<Album>> =
        repository.observeAlbums()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Pull-to-refresh (design G1-D7 item 13): forces a full re-enumeration; the grid re-emits when the
    // re-scan lands. Re-entrant pulls while one is in flight are ignored.
    private val refreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.refresh()
            } finally {
                refreshing.value = false
            }
        }
    }

    // --- In-album Sort + Grid size + scope (G1-9, design APP-465 TB-03) ---------------------------

    /** Persist a new [sort] into the album's current scope (this album only vs all). */
    fun setSort(sort: SortSpec) = persistView { viewPreferences.setSort(bucketId, sort, currentScope()) }

    /**
     * Persist a new [groupBy] time-sectioning into the album's current scope (APP-499). Fired by the
     * shared 3-dot menu's Group-by option, identical to the Photos tab; the grid re-sections in place
     * with no rescan (composition-order Filter → Sort → Group).
     */
    fun setGroupBy(groupBy: GroupBy) =
        persistView { viewPreferences.setGroupBy(bucketId, groupBy, currentScope()) }

    /**
     * Persist a new [columns] density into the album's current scope. Fired by both the Grid-size sheet
     * and a pinch-zoom, so the two share one persisted source of truth.
     */
    fun setColumns(columns: ColumnCount) =
        persistView { viewPreferences.setColumns(bucketId, columns, currentScope()) }

    /** Flip whether this album's view settings apply to itself only or to all albums. */
    fun setScope(scope: ViewScope) = persistView { viewPreferences.setScope(bucketId, scope) }

    /** The scope the album is currently on; new Sort/Grid-size writes target this store. */
    private fun currentScope(): ViewScope = viewSettings.value.scope

    private fun persistView(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // --- Create album from the shared 3-dot menu (APP-499: album menu == home menu) ---------------

    private val createAlbumResults = Channel<CreateAlbumResult>(Channel.BUFFERED)

    /** Create-album outcomes; the screen routes Success into the new album's empty "Add photos" prompt. */
    val createAlbumEvents: Flow<CreateAlbumResult> = createAlbumResults.receiveAsFlow()

    /** Create a new album folder (spec §6), same op the Albums/Photos tabs fire. */
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

    fun toggleSelection(id: MediaId) = selectionController.toggle(id)
    fun beginSelection(id: MediaId) = selectionController.beginDrag(id)
    fun dragSelectTo(id: MediaId, ordered: List<MediaId>) = selectionController.dragOver(id, ordered)
    fun selectAll(all: Collection<MediaId>) = selectionController.selectAll(all)
    fun clearSelection() = selectionController.clearSelection()
    fun runBulk(action: BulkAction, destinationBucketId: String?) =
        selectionController.run(action, destinationBucketId)
    fun runBulkToNewAlbum(action: BulkAction, name: String) =
        selectionController.runToNewAlbum(action, name)
    fun cancelBulk() = selectionController.cancel()
    fun dismissBulkResult() = selectionController.dismissResult()

    // --- Capture straight into album (spec C1-09 item 9, APP-424) ---------------------------------

    /** Only a real device folder is a valid capture target; the smart-album sentinels never are. */
    private val isRealFolder: Boolean =
        bucketId != AlbumsCatalog.RECENT_BUCKET_ID && bucketId != AlbumsCatalog.ALL_VIDEOS_BUCKET_ID

    /** The pending capture awaiting its camera result; held across the activity-result round-trip. */
    private var pendingCapture: AlbumCapture? = null

    private val _launchCapture = MutableSharedFlow<AlbumCapture>(extraBufferCapacity = 1)

    /**
     * Emits when a capture Uri is ready to hand to the system camera. The screen collects this and fires
     * the `ACTION_IMAGE_CAPTURE` launcher with [AlbumCapture.outputUri]; the ViewModel keeps the handle
     * so it can [commit][AlbumCapture.commit]/[abort][AlbumCapture.abort] once the result lands.
     */
    val launchCapture: SharedFlow<AlbumCapture> = _launchCapture.asSharedFlow()

    init {
        // Startup housekeeping: clear our own orphaned pending capture rows from a prior crashed capture
        // (Security gate APP-426). Best-effort; a real device folder screen is a natural trigger point.
        if (isRealFolder) {
            viewModelScope.launch { runCatching { operations.sweepOrphanedCaptures() } }
        }
    }

    /**
     * Mint a pending capture into *this* album (by [title], name-scoped like create) and, when ready,
     * emit its handle so the screen can launch the system camera. A no-op for smart albums or an invalid
     * name (nothing is minted). The captured item lands in the folder, so the album appears holding its
     * cover once the result is committed.
     */
    fun requestCapture(kind: CaptureKind) {
        if (!isRealFolder) return
        viewModelScope.launch {
            val capture = runCatching { operations.beginCapture(title, kind) }.getOrNull() ?: return@launch
            pendingCapture = capture
            _launchCapture.emit(capture)
        }
    }

    /**
     * Resolve the pending capture once the camera returns: [success] (`RESULT_OK`) publishes the captured
     * item (the index refreshes reactively off the MediaStore change, so the album surfaces on its own);
     * otherwise it is aborted, leaving no orphan album or row (acceptance: "Cancel leaves no orphan").
     */
    fun onCaptureResult(success: Boolean) {
        val capture = pendingCapture ?: return
        pendingCapture = null
        viewModelScope.launch {
            runCatching { if (success) capture.commit() else capture.abort() }
        }
    }
}
