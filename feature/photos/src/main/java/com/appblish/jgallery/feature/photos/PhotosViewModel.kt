package com.appblish.jgallery.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    repository: MediaIndexRepository,
    private val operations: MediaOperationsRepository,
    private val preferences: PhotosPreferences,
    @TimelineDispatcher timelineDispatcher: CoroutineDispatcher,
) : ViewModel() {

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
    )
    val selection get() = selectionController.selection
    val bulk get() = selectionController.bulk

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
}

/** Outcome of a Photos-overflow "Create album" (design G1-D7 §2). */
sealed interface PhotosCreateAlbumResult {
    data class Success(val name: String) : PhotosCreateAlbumResult
    data class Failure(val reason: String) : PhotosCreateAlbumResult
}
