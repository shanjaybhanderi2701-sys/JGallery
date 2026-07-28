package com.appblish.jgallery.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.FavoritesStore
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.TimelineSpec
import com.appblish.jgallery.core.ui.selection.BulkAction
import com.appblish.jgallery.core.ui.selection.MediaSelectionController
import com.appblish.jgallery.core.ui.share.MediaShareRequest
import com.appblish.jgallery.core.ui.share.ShareIntents
import com.appblish.jgallery.feature.photos.di.TimelineDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/** View state for the Photos tab. [Loading] is only ever the FIRST index run (design a13). */
sealed interface PhotosUiState {
    data object Loading : PhotosUiState
    data object Empty : PhotosUiState

    /**
     * The windowed timeline for the active [filter] (design C1-06). [timeline] holds the skeleton
     * (section structure) and the window cache (loaded tile pages). A truly empty library — or a
     * filter with no matches — emits [Empty] instead.
     */
    data class Content(
        val timeline: WindowedPhotosTimeline,
        val filter: MediaFilter = MediaFilter.ALL,
    ) : PhotosUiState
}

/**
 * Photos tab (spec §4): observes the SQL-derived timeline skeleton and pages tile data on demand
 * (APP-700, Option B). The skeleton — a handful of [SectionRun] rows — is kept in O(#sections)
 * memory; the N-length tile payload is held only in the [WINDOW_SIZE]-bounded window cache. The
 * composable never touches the index or the storage layer.
 */
@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val repository: MediaIndexRepository,
    private val operations: MediaOperationsRepository,
    private val preferences: PhotosPreferences,
    private val favoritesStore: FavoritesStore,
    @TimelineDispatcher private val timelineDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** The user's favorited ids (G2 · APP-543); drives the per-tile heart. One source of truth. */
    val favorites: StateFlow<Set<MediaId>> =
        favoritesStore.favoriteIds
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Star / un-star a single item (viewer header / single-select overflow path). */
    fun toggleFavorite(id: MediaId) {
        viewModelScope.launch { favoritesStore.toggle(id) }
    }

    /**
     * Bulk Add/Remove-to-Favorites from the selection overflow (APP-670, spec §5). Idempotent set writes
     * over the whole selection — starring an already-favorite (or clearing a non-favorite) is a no-op — so
     * "Add" over a mixed selection favorites the remainder and Undo is a clean inverse.
     */
    fun setFavorites(ids: Set<MediaId>, favorite: Boolean) {
        viewModelScope.launch { ids.forEach { favoritesStore.setFavorite(it, favorite) } }
    }

    // The top-bar format filter (design C1-06). In-session state, All by default; re-filters the
    // in-memory index with no rescan. A truly empty library still shows the whole-library empty state;
    // a non-empty library with an empty *filtered* result yields a Content with an empty timeline so
    // the screen can keep the chip row and show a filter-scoped empty state.
    private val filterState = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = filterState.asStateFlow()

    // Time-sectioning dimension (design G1-10). Persisted per tab.
    val groupBy: StateFlow<GroupBy> =
        preferences.groupBy
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupBy.DAY)

    // Sort order for the stream (design G1-D7 §3). Persisted per tab.
    val sort: StateFlow<SortSpec> =
        preferences.sort
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SortSpec())

    /**
     * The fully-resolved spec for the current filter + group + sort combination. Eagerly started —
     * it must be stable before [state] subscribes so the first emission is not missed.
     */
    private val specFlow: StateFlow<TimelineSpec> = combine(
        filterState,
        preferences.groupBy,
        preferences.sort,
    ) { filter, groupBy, sort ->
        TimelineSpec(filter = filter, sort = sort, groupBy = groupBy)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TimelineSpec())

    /**
     * In-memory window cache: maps page-offset (multiple of [WINDOW_SIZE]) → the items loaded for
     * that page. Cleared whenever [specFlow] changes so stale pages from the previous spec never
     * appear under the new ordering/filter.
     */
    private val windowCache = MutableStateFlow<Map<Int, List<MediaItem>>>(emptyMap())

    /**
     * Ordered ids for drag range-select. Empty until [beginSelection] is called, at which point the
     * full filtered+sorted id list is loaded in the background via [mediaIdsMatching]. The screen
     * collects this and passes it to the grid's drag handler.
     *
     * Declared before [init] because the init-block collector below writes [_allOrderedIds] and the
     * eager [specFlow] can emit synchronously during construction (Dispatchers.Main.immediate); a
     * later declaration would leave this field null at that point → NPE on launch (APP-700 regression).
     */
    private val _allOrderedIds = MutableStateFlow<List<MediaId>>(emptyList())
    val allOrderedIds: StateFlow<List<MediaId>> = _allOrderedIds.asStateFlow()

    init {
        // Clear window cache and ordered-ids whenever the query changes.
        viewModelScope.launch {
            specFlow.collect {
                windowCache.value = emptyMap()
                _allOrderedIds.value = emptyList()
            }
        }
        // Keep already-loaded window pages coherent with incremental index changes (a new capture,
        // a delete, a rename). observeTimelineSkeleton re-emits only on a real content change; when it
        // does, re-fetch the offsets currently in the cache — and the ordered-id list, if a selection
        // is live — so the visible viewport reflects the change instead of showing stale/deleted tiles
        // until the user scrolls the first row off-screen. Guarded on a non-empty cache, so a library
        // that has never been paged (or a no-op signal collapsed by distinctUntilChanged) costs nothing.
        viewModelScope.launch {
            specFlow
                .flatMapLatest { spec -> repository.observeTimelineSkeleton(spec) }
                .collect {
                    val offsets = windowCache.value.keys
                    if (offsets.isEmpty()) return@collect
                    val spec = specFlow.value
                    val refreshed = offsets.associateWith { offset ->
                        repository.loadWindow(spec, offset = offset, limit = WINDOW_SIZE)
                    }
                    // Merge over the current cache so a page loaded during the reload isn't dropped.
                    windowCache.value = windowCache.value + refreshed
                    if (_allOrderedIds.value.isNotEmpty()) {
                        _allOrderedIds.value = repository.mediaIdsMatching(spec)
                    }
                }
        }
    }

    val state: StateFlow<PhotosUiState> = combine(
        specFlow.flatMapLatest { spec -> repository.observeTimelineSkeleton(spec) },
        windowCache,
        specFlow,
    ) { skeleton, cache, spec ->
        if (skeleton.totalItems == 0) {
            PhotosUiState.Empty
        } else {
            val zone = ZoneId.systemDefault()
            PhotosUiState.Content(
                timeline = WindowedPhotosTimeline(
                    skeleton = skeleton,
                    windowCache = cache,
                    zone = zone,
                    today = LocalDate.now(zone),
                    locale = Locale.getDefault(),
                    sort = spec.sort,
                    groupBy = spec.groupBy,
                ),
                filter = spec.filter,
            )
        }
    }
        .flowOn(timelineDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosUiState.Loading)

    /**
     * Load the window page that contains [ordinal] and evict the farthest pages if the cache grows
     * beyond [MAX_CACHED_WINDOWS]. Idempotent — re-requesting an already-cached offset is a no-op.
     */
    fun loadWindowFor(ordinal: Int) {
        if (ordinal < 0) return
        val windowOffset = (ordinal / WINDOW_SIZE) * WINDOW_SIZE
        if (windowCache.value.containsKey(windowOffset)) return
        val spec = specFlow.value
        viewModelScope.launch {
            val items = repository.loadWindow(spec, offset = windowOffset, limit = WINDOW_SIZE)
            val newCache = windowCache.value.toMutableMap()
            newCache[windowOffset] = items
            // LRU eviction: keep the MAX_CACHED_WINDOWS pages nearest to this offset.
            if (newCache.size > MAX_CACHED_WINDOWS) {
                newCache.keys
                    .sortedByDescending { abs(it - windowOffset) }
                    .drop(MAX_CACHED_WINDOWS)
                    .forEach { newCache.remove(it) }
            }
            windowCache.value = newCache
        }
    }

    /**
     * Virtual select-all (D6): loads all matching ids via a single SQL projection
     * ([mediaIdsMatching]) instead of materializing the full tile list. Updates the selection
     * controller once the coroutine completes.
     */
    fun selectAllFiltered() {
        viewModelScope.launch {
            val ids = repository.mediaIdsMatching(specFlow.value)
            selectionController.selectAll(ids)
        }
    }

    // Pull-to-refresh (design G1-D7 item 13): forces a full re-enumeration of the index. Re-entrant
    // pulls while one is in flight are ignored.
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

    fun setFilter(filter: MediaFilter) {
        filterState.value = filter
    }

    fun setGroupBy(groupBy: GroupBy) {
        viewModelScope.launch { preferences.setGroupBy(groupBy) }
    }

    fun setSort(sort: SortSpec) {
        viewModelScope.launch { preferences.setSort(sort) }
    }

    // Create album (design G1-D7 §2).
    private val createAlbumResults = Channel<PhotosCreateAlbumResult>(Channel.BUFFERED)
    val createAlbumEvents: Flow<PhotosCreateAlbumResult> = createAlbumResults.receiveAsFlow()

    fun createAlbum(name: String) {
        viewModelScope.launch {
            val result = operations.createAlbum(name)
            createAlbumResults.send(
                if (result.succeeded > 0) {
                    PhotosCreateAlbumResult.Success(name.trim())
                } else {
                    PhotosCreateAlbumResult.Failure(
                        result.failures.firstOrNull()?.reason ?: "Couldn't create album",
                    )
                },
            )
        }
    }

    // Rename a single selected photo/video (device-test item 1, APP-494).
    private val renameResults = Channel<PhotosRenameResult>(Channel.BUFFERED)
    val renameEvents: Flow<PhotosRenameResult> = renameResults.receiveAsFlow()

    fun renameSelected(id: MediaId, newName: String) {
        viewModelScope.launch {
            val result = operations.rename(id, newName)
            renameResults.send(
                if (result.succeeded > 0) {
                    PhotosRenameResult.Success(newName.trim())
                } else {
                    PhotosRenameResult.Failure(
                        result.failures.firstOrNull()?.reason ?: "Couldn't rename",
                    )
                },
            )
        }
    }

    val columns: StateFlow<ColumnCount> =
        preferences.columns
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColumnCount.DEFAULT)

    /**
     * Multi-select + bulk ops (spec §7.6), driven off the retained [viewModelScope] so a running
     * copy survives config changes.
     */
    val selectionController = MediaSelectionController(
        scope = viewModelScope,
        copy = operations::copy,
        move = operations::move,
        trash = operations::moveToTrash,
        copyToNew = operations::copyToNewAlbum,
        moveToNew = operations::moveToNewAlbum,
        export = operations::exportCopy,
    )
    val selection get() = selectionController.selection
    val bulk get() = selectionController.bulk

    /** Export ("Save a copy") the current selection into the SAF folder [treeUri] (G2 · APP-549). */
    fun exportSelected(treeUri: android.net.Uri) = selectionController.runExport(treeUri)

    /** Destination list for the Copy to / Move to picker (spec §7.1/§7.2). */
    val destinations: StateFlow<List<Album>> =
        repository.observeAlbums()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setColumns(columns: ColumnCount) {
        viewModelScope.launch { preferences.setColumns(columns) }
    }

    // Selection intents forwarded to the shared controller.
    fun toggleSelection(id: MediaId) = selectionController.toggle(id)

    /**
     * Begin a drag-selection from [id] and lazily load all ordered ids for range-selection. The ids
     * are needed by the drag handler to compute the selected range; they are fetched once per
     * selection session and reused for subsequent drag events.
     */
    fun beginSelection(id: MediaId) {
        selectionController.beginDrag(id)
        viewModelScope.launch {
            if (_allOrderedIds.value.isEmpty()) {
                _allOrderedIds.value = repository.mediaIdsMatching(specFlow.value)
            }
        }
    }

    fun dragSelectTo(id: MediaId, ordered: List<MediaId>) = selectionController.dragOver(id, ordered)
    fun selectAll(all: Collection<MediaId>) = selectionController.selectAll(all)
    fun clearSelection() = selectionController.clearSelection()
    fun runBulk(action: BulkAction, destinationBucketId: String?) =
        selectionController.run(action, destinationBucketId)
    fun runBulkToNewAlbum(action: BulkAction, name: String) =
        selectionController.runToNewAlbum(action, name)
    fun cancelBulk() = selectionController.cancel()
    fun dismissBulkResult() = selectionController.dismissResult()

    // Share the current selection (G2 · APP-541). Uses a targeted MediaQuery so only the selected
    // items are fetched — avoids materializing the full library.
    private val shareRequests = Channel<MediaShareRequest>(Channel.BUFFERED)
    val shareEvents: Flow<MediaShareRequest> = shareRequests.receiveAsFlow()

    fun shareSelected() {
        val ids = selectionController.selection.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val byId = repository.observeMedia(MediaQuery(ids = ids.toSet())).first()
                .associateBy { it.id }
            val resolved = ids.mapNotNull { id ->
                val uri = operations.viewUri(id) ?: return@mapNotNull null
                uri to byId[id]?.mimeType
            }
            shareRequests.send(
                if (resolved.isEmpty()) {
                    MediaShareRequest.Empty
                } else {
                    MediaShareRequest.Ready(
                        uris = resolved.map { it.first },
                        mimeType = ShareIntents.commonMimeType(resolved.map { it.second }),
                    )
                },
            )
        }
    }

    private companion object {
        const val WINDOW_SIZE = WindowedPhotosTimeline.WINDOW_SIZE
        const val MAX_CACHED_WINDOWS = 5
    }
}

/** Outcome of a Photos-overflow "Create album" (design G1-D7 §2). */
sealed interface PhotosCreateAlbumResult {
    data class Success(val name: String) : PhotosCreateAlbumResult
    data class Failure(val reason: String) : PhotosCreateAlbumResult
}

/** Outcome of a single-select "Rename" (device-test item 1, APP-494). */
sealed interface PhotosRenameResult {
    data class Success(val name: String) : PhotosRenameResult
    data class Failure(val reason: String) : PhotosRenameResult
}
