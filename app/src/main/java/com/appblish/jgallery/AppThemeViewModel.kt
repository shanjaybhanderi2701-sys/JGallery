package com.appblish.jgallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.model.ThemeMode
import com.appblish.jgallery.feature.settings.SettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Root theme resolver (design G2 Settings §3): reads the app-wide [ThemeMode] off the Settings
 * DataStore and exposes it to [MainActivity], which threads it into the root `JGalleryTheme` so the
 * whole NavHost re-themes on change. Starts on [ThemeMode.SYSTEM] so first composition matches the
 * historical device-follows default with no flash.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    preferences: SettingsPreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        preferences.themeMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )
}
