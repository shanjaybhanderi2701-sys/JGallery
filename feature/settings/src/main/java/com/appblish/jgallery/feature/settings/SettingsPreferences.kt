package com.appblish.jgallery.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appblish.jgallery.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-wide Settings preferences (design G2 Settings §2/§3). Mirrors the per-feature DataStore idiom
 * (see `PhotosPreferences`): an interface so the ViewModel unit-tests against an in-memory fake, with
 * the DataStore binding in [di.SettingsModule].
 *
 * Holds the Settings-owned view prefs — theme and the default slideshow interval. The app-wide default
 * sort + grid density moved out to the shared `:core:viewdefaults` seam (APP-569) so Photos/Albums can
 * seed-read them without a `:feature → :feature` edge; Settings writes them via `ViewDefaults`. Values
 * that fall outside their valid range are clamped/defaulted on read, so a momentarily-null store always
 * renders a sensible default and never a spinner (§2 state model).
 */
interface SettingsPreferences {

    /** Theme mode; default [ThemeMode.SYSTEM] (follows device dark-mode). */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** Default slideshow interval in ms (§6); default [DEFAULT_SLIDESHOW_INTERVAL_MS] (4s). */
    val slideshowIntervalMs: Flow<Long>

    suspend fun setSlideshowIntervalMs(ms: Long)

    companion object {
        const val DEFAULT_SLIDESHOW_INTERVAL_MS: Long = 4_000L
    }
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

    override val slideshowIntervalMs: Flow<Long> =
        dataStore.data.map { prefs ->
            prefs[KEY_SLIDESHOW_MS] ?: SettingsPreferences.DEFAULT_SLIDESHOW_INTERVAL_MS
        }

    override suspend fun setSlideshowIntervalMs(ms: Long) {
        dataStore.edit { it[KEY_SLIDESHOW_MS] = ms }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_SLIDESHOW_MS = longPreferencesKey("slideshow_interval_ms")
    }
}
