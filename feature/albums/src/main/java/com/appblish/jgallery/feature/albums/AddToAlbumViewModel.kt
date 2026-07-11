package com.appblish.jgallery.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** View state for the whole-library "Add photos to this album" picker (design C1-09, W2-11). */
sealed interface AddToAlbumUiState {
    data object Loading : AddToAlbumUiState
    data object Empty : AddToAlbumUiState
    data class Content(val items: List<MediaItem>) : AddToAlbumUiState
}

/** Terminal outcome of an "Add N" confirm, surfaced so the picker can toast + pop back. */
data class AddToAlbumResult(val added: Int, val failed: Int)

/**
 * The "Add photos" picker a freshly-created album lands on (design C1-09, spec §6/§7.1). It reuses the
 * W2-11 selection idea over the **whole library** — a flat newest-first grid where every tap toggles
 * selection — then copies the chosen items **into the album by name** through the §1.6 create-and-fill
 * seam ([MediaOperationsRepository.copyToNewAlbum], APP-422). Addressing the destination by name (not a
 * synthetic row-less bucket handle) is the boundary-pure path the Architect ruled for a just-created
 * album: the flow creates/reuses the folder and lands the items in one transaction, so the album is
 * born holding its contents and its first item becomes the cover (via the index). No storage-layer or
 * §1.6 change is introduced by this feature.
 */
@HiltViewModel
class AddToAlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MediaIndexRepository,
    private val operations: MediaOperationsRepository,
) : ViewModel() {

    /** The album being added to — the create-and-fill destination, addressed by name (APP-297 pure). */
    val albumName: String = checkNotNull(savedStateHandle[ADD_TO_ALBUM_NAME_ARG]) {
        "AddToAlbum requires an album name argument"
    }

    private val selectedIds = MutableStateFlow<Set<MediaId>>(emptySet())
    val selected: StateFlow<Set<MediaId>> = selectedIds.asStateFlow()

    /** True while the copy is running — the Add button locks to avoid a double-submit. */
    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    private val results = Channel<AddToAlbumResult>(Channel.BUFFERED)

    /** One-shot "added N" outcome; the screen toasts it and pops back to the now-populated album. */
    val addEvents: Flow<AddToAlbumResult> = results.receiveAsFlow()

    val state: StateFlow<AddToAlbumUiState> =
        repository.observeMedia(
            MediaQuery(sort = SortSpec(SortKey.LAST_MODIFIED, SortDirection.DESCENDING)),
        )
            .map { items -> if (items.isEmpty()) AddToAlbumUiState.Empty else AddToAlbumUiState.Content(items) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddToAlbumUiState.Loading)

    fun toggle(id: MediaId) {
        selectedIds.update { current -> if (id in current) current - id else current + id }
    }

    /**
     * Copy the current selection into the album [albumName] via the create-and-fill seam (spec §7.1).
     * Folds the E8 event stream to its terminal summary and emits an [AddToAlbumResult]; a no-op when
     * nothing is selected or a copy is already in flight.
     */
    fun confirm() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty() || _adding.value) return
        _adding.value = true
        viewModelScope.launch {
            val summary = when (val terminal = operations.copyToNewAlbum(ids, albumName).last()) {
                is FileOperationEvent.Completed -> terminal.summary
                else -> null
            }
            _adding.value = false
            results.send(AddToAlbumResult(added = summary?.succeeded ?: 0, failed = summary?.failed ?: 0))
        }
    }
}
