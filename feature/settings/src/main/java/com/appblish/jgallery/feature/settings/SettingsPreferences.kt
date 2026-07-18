package com.appblish.jgallery.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-wide Settings preferences (design G2 Settings §2/§3). Mirrors the per-feature DataStore idiom
 * (see `PhotosPreferences`): an interface so the ViewModel unit-tests against an in-memory fake, with
 * the DataStore binding in [di.SettingsModule].
 *
 * Holds only view-default prefs — theme, the app-wide default sort + grid density, and the default
 * slideshow interval. Values that fall outside their valid range are clamped/defaulted on read, so a
 * momentarily-null store always renders a sensible default and never a spinner (§2 state model).
 */
interface SettingsPreferences {

    /** Theme mode; default [ThemeMode.SYSTEM] (follows device dark-mode). */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** App-wide default sort seed (§3); default = Last modified, descending. */
    val defaultSort: Flow<SortSpec>

    suspend fun setDefaultSort(sort: SortSpec)

    /** App-wide default grid density (§3); default 3 columns, clamped to 2..6. */
    val defaultColumns: Flow<ColumnCount>

    suspend fun setDefaultColumns(columns: ColumnCount)

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

    override val defaultSort: Flow<SortSpec> =
        dataStore.data.map { prefs ->
            val key = prefs[KEY_SORT_KEY]?.let { name ->
                runCatching { SortKey.valueOf(name) }.getOrNull()
            } ?: SortKey.LAST_MODIFIED
            val dir = prefs[KEY_SORT_DIR]?.let { name ->
                runCatching { SortDirection.valueOf(name) }.getOrNull()
            } ?: SortDirection.DESCENDING
            SortSpec(key, dir)
        }

    override suspend fun setDefaultSort(sort: SortSpec) {
        dataStore.edit {
            it[KEY_SORT_KEY] = sort.key.name
            it[KEY_SORT_DIR] = sort.direction.name
        }
    }

    override val defaultColumns: Flow<ColumnCount> =
        dataStore.data.map { prefs ->
            prefs[KEY_COLUMNS]?.let(ColumnCount::clamp) ?: ColumnCount.DEFAULT
        }

    override suspend fun setDefaultColumns(columns: ColumnCount) {
        dataStore.edit { it[KEY_COLUMNS] = columns.value }
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
        val KEY_SORT_KEY = stringPreferencesKey("default_sort_key")
        val KEY_SORT_DIR = stringPreferencesKey("default_sort_dir")
        val KEY_COLUMNS = intPreferencesKey("default_columns")
        val KEY_SLIDESHOW_MS = longPreferencesKey("slideshow_interval_ms")
    }
}
