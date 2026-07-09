package com.appblish.jgallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
 * Albums tab (spec §3, design a04): device folders with cover + count, served straight from the
 * cached index, newest-content-first (the DAO's aggregation order). No file IO, no MediaStore —
 * covers load through the same E4 pipeline as every grid tile.
 */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    repository: MediaIndexRepository,
    private val preferences: AlbumsPreferences,
) : ViewModel() {

    val state: StateFlow<AlbumsUiState> =
        repository.observeAlbums()
            .map { albums ->
                if (albums.isEmpty()) AlbumsUiState.Empty else AlbumsUiState.Content(albums)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsUiState.Loading)

    val columns: StateFlow<ColumnCount> =
        preferences.columns
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColumnCount.DEFAULT)

    fun setColumns(columns: ColumnCount) {
        viewModelScope.launch { preferences.setColumns(columns) }
    }
}
