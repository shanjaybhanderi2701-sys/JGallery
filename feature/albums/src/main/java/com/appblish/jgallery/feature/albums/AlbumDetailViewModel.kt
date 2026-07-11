package com.appblish.jgallery.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.ui.selection.BulkAction
import com.appblish.jgallery.core.ui.selection.MediaSelectionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MediaIndexRepository,
    operations: MediaOperationsRepository,
) : ViewModel() {

    /** The bucket being viewed; excluded from Copy/Move destinations so you can't target the source. */
    val bucketId: String = checkNotNull(savedStateHandle[ALBUM_DETAIL_BUCKET_ID_ARG]) {
        "AlbumDetail requires a bucketId argument"
    }
    val title: String = savedStateHandle.get<String>(ALBUM_DETAIL_NAME_ARG) ?: "Album"
    private val videoOnly: Boolean = savedStateHandle[ALBUM_DETAIL_VIDEO_ONLY_ARG] ?: false

    /**
     * Translate the nav args into a cache query. Real folders scope by [bucketId]; the smart-album
     * sentinels (spec C4) map to library-wide queries — Recent = whole library newest-first,
     * All-Videos = every video. [videoOnly] narrows a real folder to its videos (Video → folder-wise).
     */
    private val query: MediaQuery = when (bucketId) {
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

    val state: StateFlow<AlbumDetailUiState> =
        repository.observeMedia(query)
            .map { items ->
                if (items.isEmpty()) AlbumDetailUiState.Empty else AlbumDetailUiState.Content(items)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumDetailUiState.Loading)

    val selectionController = MediaSelectionController(
        scope = viewModelScope,
        copy = operations::copy,
        move = operations::move,
        trash = operations::moveToTrash,
    )
    val selection get() = selectionController.selection
    val bulk get() = selectionController.bulk

    val destinations: StateFlow<List<Album>> =
        repository.observeAlbums()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleSelection(id: MediaId) = selectionController.toggle(id)
    fun beginSelection(id: MediaId) = selectionController.beginDrag(id)
    fun dragSelectTo(id: MediaId, ordered: List<MediaId>) = selectionController.dragOver(id, ordered)
    fun selectAll(all: Collection<MediaId>) = selectionController.selectAll(all)
    fun clearSelection() = selectionController.clearSelection()
    fun runBulk(action: BulkAction, destinationBucketId: String?) =
        selectionController.run(action, destinationBucketId)
    fun cancelBulk() = selectionController.cancel()
    fun dismissBulkResult() = selectionController.dismissResult()
}
