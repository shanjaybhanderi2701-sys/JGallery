package com.appblish.jgallery.feature.photos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Photos-tab view settings, persisted per tab (design §3: density survives tab switches AND process
 * death). Interface so the ViewModel unit-tests with an in-memory fake; the DataStore binding lives
 * in [di.PhotosModule].
 */
interface PhotosPreferences {

    /** Grid column count for the Photos tab (2–6, default 3). */
    val columns: Flow<ColumnCount>

    suspend fun setColumns(columns: ColumnCount)

    /** Time-sectioning dimension for the Photos stream (design G1-10, default [GroupBy.DAY]). */
    val groupBy: Flow<GroupBy>

    suspend fun setGroupBy(groupBy: GroupBy)

    /**
     * Sort order for the Photos stream (design G1-D7 §3 — wires the shared SortBySheet to Photos).
     * Persisted per tab and independent of Albums; default [SortSpec] = Last Modified, descending.
     */
    val sort: Flow<SortSpec>

    suspend fun setSort(sort: SortSpec)
}

/**
 * DataStore-backed [PhotosPreferences]. Stored values outside 2–6 are clamped on read.
 *
 * APP-569 seed: a pref the Photos tab has never set falls back to the app-wide [ViewDefaults] (the
 * "Default sort" / "Grid density" written in Settings) instead of a hard-coded constant. Once the tab
 * writes its own value the per-tab key wins and later changes to the default no longer move it, so an
 * existing per-tab override is untouched.
 */
internal class DataStorePhotosPreferences(
    private val dataStore: DataStore<Preferences>,
    private val viewDefaults: ViewDefaults,
) : PhotosPreferences {

    override val columns: Flow<ColumnCount> =
        combine(dataStore.data, viewDefaults.defaultColumns) { prefs, default ->
            prefs[KEY_COLUMNS]?.let(ColumnCount::clamp) ?: default
        }

    override suspend fun setColumns(columns: ColumnCount) {
        dataStore.edit { it[KEY_COLUMNS] = columns.value }
    }

    override val groupBy: Flow<GroupBy> =
        dataStore.data.map { prefs ->
            // Stored as the enum ordinal; anything out of range falls back to the DAY default.
            prefs[KEY_GROUP_BY]
                ?.let { GroupBy.entries.getOrNull(it) }
                ?: GroupBy.DAY
        }

    override suspend fun setGroupBy(groupBy: GroupBy) {
        dataStore.edit { it[KEY_GROUP_BY] = groupBy.ordinal }
    }

    override val sort: Flow<SortSpec> =
        combine(dataStore.data, viewDefaults.defaultSort) { prefs, default ->
            // Stored as two enum ordinals; with no per-tab key the tab seeds from the app-wide default
            // (APP-569), and a stored key out of range likewise falls back to it.
            val key = prefs[KEY_SORT_KEY]?.let { SortKey.entries.getOrNull(it) } ?: return@combine default
            val direction = prefs[KEY_SORT_DIR]?.let { SortDirection.entries.getOrNull(it) }
                ?: SortDirection.DESCENDING
            SortSpec(key = key, direction = direction)
        }

    override suspend fun setSort(sort: SortSpec) {
        dataStore.edit {
            it[KEY_SORT_KEY] = sort.key.ordinal
            it[KEY_SORT_DIR] = sort.direction.ordinal
        }
    }

    private companion object {
        val KEY_COLUMNS = intPreferencesKey("photos_columns")
        val KEY_GROUP_BY = intPreferencesKey("photos_group_by")
        val KEY_SORT_KEY = intPreferencesKey("photos_sort_key")
        val KEY_SORT_DIR = intPreferencesKey("photos_sort_dir")
    }
}
