package com.appblish.jgallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** View state for the Video smart album's folder-wise grouping (spec C4 item 5). */
sealed interface VideoAlbumsUiState {
    data object Loading : VideoAlbumsUiState
    data object Empty : VideoAlbumsUiState
    data class Content(val albums: List<Album>) : VideoAlbumsUiState
}

/**
 * The Video smart album's contents (spec C4 items 4 & 5): an "All Videos" card followed by one
 * sub-album per folder that holds videos, built by [AlbumsCatalog.videoFolderAlbums] straight from the
 * cached index's video subset. Reuses the Albums-tab column density so the grid feels consistent.
 */
@HiltViewModel
class VideoAlbumsViewModel @Inject constructor(
    repository: MediaIndexRepository,
    private val preferences: AlbumsPreferences,
) : ViewModel() {

    val state: StateFlow<VideoAlbumsUiState> =
        repository.observeMedia(MediaQuery(types = setOf(MediaType.VIDEO)))
            .map { videos ->
                val albums = AlbumsCatalog.videoFolderAlbums(videos)
                if (albums.isEmpty()) VideoAlbumsUiState.Empty else VideoAlbumsUiState.Content(albums)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoAlbumsUiState.Loading)

    val columns: StateFlow<ColumnCount> =
        preferences.columns
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColumnCount.DEFAULT)

    fun setColumns(columns: ColumnCount) {
        viewModelScope.launch { preferences.setColumns(columns) }
    }
}
