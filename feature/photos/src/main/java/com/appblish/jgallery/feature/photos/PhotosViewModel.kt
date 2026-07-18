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
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.filteredBy
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** View state for the Photos tab. [Loading] is only ever the FIRST index run (design a13). */
sealed interface PhotosUiState {
    data object Loading : PhotosUiState
    data object Empty : PhotosUiState

    /**
     * The time-grouped stream for the active [filter] (design C1-06). [timeline] is already filtered;
     * an empty [timeline] here means the library is non-empty but nothing matches the filter, which the
     * screen renders as a filter-scoped empty state (keeping the chip row visible).
     */
    data class Content(
        val timeline: PhotosTimeline,
        val filter: MediaFilter = MediaFilter.ALL,
    ) : PhotosUiState
}

/**
 * Photos tab (spec §4): observes the whole cached index and precomputes the date-grouped stream on
 * the injected [TimelineDispatcher] (Default in production) — by the time Compose sees an emission
 * there is zero remaining per-frame work. The screen itself never touches the index, the storage
 * layer, or a clock.
 */
@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val repository: MediaIndexRepository,
    private val operations: MediaOperationsRepository,
    private val preferences: PhotosPreferences,
    private val favoritesStore: FavoritesStore,
    @TimelineDispatcher timelineDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** The user's favorited ids (G2 · APP-543); drives the per-tile heart. One source of truth. */
    val favorites: StateFlow<Set<MediaId>> =
        favoritesStore.favoriteIds
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Star / un-star a tile in place from the grid (does not open it). */
    fun toggleFavorite(id: MediaId) {
        viewModelScope.launch { favoritesStore.toggle(id) }
    }

    // The top-bar format filter (design C1-06). In-session state, All by default; re-filters the
    // in-memory index with no rescan. A truly empty library still shows the whole-library empty state;
    // a non-empty library with an empty *filtered* result yields a Content with an empty timeline so
    // the screen can keep the chip row and show a filter-scoped empty state.
    private val filterState = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = filterState.asStateFlow()

    // Time-sectioning dimension (design G1-10). Persisted per tab so a chosen grouping survives tab
    // switches and process death, exactly like column count. Re-grouping re-derives the same
    // filtered+sorted stream on the [TimelineDispatcher] — no rescan, no IO.
    val groupBy: StateFlow<GroupBy> =
        preferences.groupBy
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupBy.DAY)

    // Sort order for the stream (design G1-D7 §3). Persisted per tab, independent of Albums; changing
    // it re-derives the same in-memory index on the timeline dispatcher — no rescan, like [groupBy].
    // APP-569 §3: the app-wide "Default sort" in Settings now SEEDS this pref — [PhotosPreferences.sort]
    // falls back to the shared `:core:viewdefaults` value when the tab has no override yet, so no
    // :feature:photos → :feature:settings edge is introduced. The per-tab overflow sort still overrides
    // and persists per tab; once set, later default changes leave it untouched.
    val sort: StateFlow<SortSpec> =
        preferences.sort
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SortSpec())

    val state: StateFlow<PhotosUiState> =
        combine(
            repository.observeMedia(MediaQuery()),
            filterState,
            preferences.groupBy,
            preferences.sort,
        ) { items, filter, groupBy, sort ->
            if (items.isEmpty()) {
                PhotosUiState.Empty
            } else {
                val zone = ZoneId.systemDefault()
                PhotosUiState.Content(
                    timeline = buildPhotosTimeline(
                        items = items.filteredBy(filter),
                        zone = zone,
                        today = LocalDate.now(zone),
                        groupBy = groupBy,
                        sort = sort,
                    ),
                    filter = filter,
                )
            }
        }
            .flowOn(timelineDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosUiState.Loading)

    // Pull-to-refresh (design G1-D7 item 13): forces a full re-enumeration of the index. The spinner
    // stays up for the duration of [MediaIndexRepository.refresh]; the observeMedia stream re-emits when
    // the re-scan lands. Re-entrant pulls while one is in flight are ignored.
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

    // Create album (design G1-D7 §2): the Photos overflow can now start a new album. Mirrors
    // AlbumsViewModel — creates the folder, then the screen routes into its empty "Add photos" prompt
    // on success (so it gets a cover) or toasts the failure reason.
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

    // Rename a single selected photo/video (device-test item 1, APP-494): reuses the same per-item
    // MediaStore rename Albums uses (operations.rename), surfacing the outcome as a one-shot toast.
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
     * Multi-select + bulk ops (spec §7.6), driven off the retained [viewModelScope] so a running copy
     * survives config changes. Copy/Move destinations are the device albums; the source (Photos =
     * whole library) is null so every album is offered.
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

    // Share the current selection to the system share sheet (G2 · APP-541). Each selected id is
    // resolved to its §1.6-sanctioned MediaStore content uri (operations.viewUri — the same deliberate
    // cross-boundary exposure the viewer's "Set as"/"Open with" use, APP-297); a null means the item
    // was deleted underneath us and is simply skipped. The narrowed common MIME type is derived from
    // the selected items' own types so the chooser filters sensibly. The screen owns the platform
    // Intent + chooser launch (this VM stays free of android.content.Intent).
    private val shareRequests = Channel<MediaShareRequest>(Channel.BUFFERED)
    val shareEvents: Flow<MediaShareRequest> = shareRequests.receiveAsFlow()

    fun shareSelected() {
        val ids = selectionController.selection.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val byId = repository.observeMedia(MediaQuery()).first().associateBy { it.id }
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

