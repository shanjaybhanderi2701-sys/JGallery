package com.appblish.jgallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    val state: StateFlow<AlbumsUiState> =
        combine(repository.observeAlbums(), preferences.sort) { albums, sort ->
            if (albums.isEmpty()) {
                AlbumsUiState.Empty
            } else {
                AlbumsUiState.Content(albums.sortedWith(sort.albumComparator()))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsUiState.Loading)

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
