package com.appblish.jgallery.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appblish.jgallery.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-wide Settings preferences (design G2 Settings §2/§3). Mirrors the per-feature DataStore idiom
 * (see `PhotosPreferences`): an interface so the ViewModel unit-tests against an in-memory fake, with
 * the DataStore binding in [di.SettingsModule].
 *
 * Holds the Settings-owned view pref — theme. The app-wide default sort + grid density moved out to the
 * shared `:core:viewdefaults` seam (APP-569), and the slideshow interval moved there too (APP-594) so
 * the viewer can read it without a `:feature → :feature` edge; Settings writes all three via
 * `ViewDefaults`. Values that fall outside their valid range are clamped/defaulted on read, so a
 * momentarily-null store always renders a sensible default and never a spinner (§2 state model).
 */
interface SettingsPreferences {

    /** Theme mode; default [ThemeMode.SYSTEM] (follows device dark-mode). */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}

/** DataStore-backed [SettingsPreferences]. Out-of-range/unknown stored values fall back on read. */
internal class DataStoreSettingsPreferences(
    private val dataStore: DataStore<Preferences>,
) : SettingsPreferences {

    override val themeMode: Flow<ThemeMode> =
        dataStore.data.map { prefs ->
            prefs[KEY_THEME_MODE]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrNull()
            } ?: ThemeMode.SYSTEM
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
