package com.appblish.jgallery.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.ThemeMode
import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable snapshot the Settings screen renders; every value has a synchronous default (§2). */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultSort: SortSpec = SortSpec(),
    val defaultColumns: ColumnCount = ColumnCount.DEFAULT,
    val slideshowIntervalMs: Long = SettingsPreferences.DEFAULT_SLIDESHOW_INTERVAL_MS,
)

/**
 * Backs the Settings screen (design G2 Settings). Reads theme + slideshow off [SettingsPreferences]
 * and the app-wide default sort + grid density off the shared [ViewDefaults] seam (APP-569), exposing
 * them as one [SettingsUiState]; each setter persists immediately (no confirm — the dialog/sheet applies
 * on select, §3). Writing the defaults through [ViewDefaults] is what seeds the Photos/Albums tabs.
 * No loading/error states by design.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: SettingsPreferences,
    private val viewDefaults: ViewDefaults,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(
            preferences.themeMode,
            viewDefaults.defaultSort,
            viewDefaults.defaultColumns,
            preferences.slideshowIntervalMs,
        ) { theme, sort, columns, slideshow ->
            SettingsUiState(theme, sort, columns, slideshow)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }

    fun setDefaultSort(sort: SortSpec) = viewModelScope.launch { viewDefaults.setDefaultSort(sort) }

    fun setDefaultColumns(columns: ColumnCount) =
        viewModelScope.launch { viewDefaults.setDefaultColumns(columns) }

    fun setSlideshowIntervalMs(ms: Long) =
        viewModelScope.launch { preferences.setSlideshowIntervalMs(ms) }
}
