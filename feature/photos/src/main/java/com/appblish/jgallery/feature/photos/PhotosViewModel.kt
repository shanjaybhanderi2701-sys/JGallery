package com.appblish.jgallery.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.feature.photos.di.TimelineDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** View state for the Photos tab. [Loading] is only ever the FIRST index run (design a13). */
sealed interface PhotosUiState {
    data object Loading : PhotosUiState
    data object Empty : PhotosUiState
    data class Content(val timeline: PhotosTimeline) : PhotosUiState
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
    private val preferences: PhotosPreferences,
    @TimelineDispatcher timelineDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<PhotosUiState> =
        repository.observeMedia(MediaQuery())
            .map { items ->
                if (items.isEmpty()) {
                    PhotosUiState.Empty
                } else {
                    val zone = ZoneId.systemDefault()
                    PhotosUiState.Content(
                        buildPhotosTimeline(items = items, zone = zone, today = LocalDate.now(zone)),
                    )
                }
            }
            .flowOn(timelineDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosUiState.Loading)

    val columns: StateFlow<ColumnCount> =
        preferences.columns
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColumnCount.DEFAULT)

    fun setColumns(columns: ColumnCount) {
        viewModelScope.launch { preferences.setColumns(columns) }
    }
}
