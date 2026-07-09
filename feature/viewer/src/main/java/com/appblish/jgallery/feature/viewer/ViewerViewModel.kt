package com.appblish.jgallery.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.playback.PlaybackSources
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    private fun resolveInitialIndex(items: List<MediaItem>): Int =
        frozenInitialIndex
            ?: items.indexOfFirst { it.id == initialId }
                .coerceAtLeast(0) // launch item already gone → open on the first item
                .also { frozenInitialIndex = it }
}
