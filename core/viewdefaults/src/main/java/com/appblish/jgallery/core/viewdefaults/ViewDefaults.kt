package com.appblish.jgallery.core.viewdefaults

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-wide **view-defaults seam** (APP-569; Architect ruling from the APP-567 review).
 *
 * The single source of truth for the "Default sort" and "Grid density" prefs surfaced in Settings.
 * Settings is the **writer**; the Photos and Albums tabs are **seed-readers** — a tab that has no
 * per-tab override yet falls back to these defaults, while an existing per-tab override still wins and
 * persists per tab (the default only seeds the initial value).
 *
 * Lives in `:core` so both features depend on it and neither depends on the other: it replaces what
 * would otherwise be an illegal `:feature:photos → :feature:settings` Gradle edge. Interface so
 * consumers unit-test against an in-memory fake; the DataStore binding lives in [di.ViewDefaultsModule].
 *
 * Out-of-range / unknown stored values are clamped or defaulted on read, so a momentarily-null store
 * always yields a sensible value and never throws.
 */
interface ViewDefaults {

    /** App-wide default sort seed; default = Last modified, descending. */
    val defaultSort: Flow<SortSpec>

    suspend fun setDefaultSort(sort: SortSpec)

    /** App-wide default grid density; default 3 columns, clamped to 2..6. */
    val defaultColumns: Flow<ColumnCount>

    suspend fun setDefaultColumns(columns: ColumnCount)

    /**
     * App-wide slideshow auto-advance interval in ms (G2 Settings §6). Settings is the **writer**; the
     * viewer is the **reader** — starting a slideshow dwells this long per image (APP-594). Lives here,
     * not in `:feature:settings`, so `:feature:viewer` can read it without an illegal `:feature →
     * :feature` edge — the same seam rationale as the sort/density defaults above. Clamped to
     * [MIN_SLIDESHOW_INTERVAL_MS]..[MAX_SLIDESHOW_INTERVAL_MS] on read; default
     * [DEFAULT_SLIDESHOW_INTERVAL_MS] (4s).
     */
    val slideshowIntervalMs: Flow<Long>

    suspend fun setSlideshowIntervalMs(ms: Long)

    companion object {
        /** Default slideshow interval; matches the Settings §6 default value. */
        const val DEFAULT_SLIDESHOW_INTERVAL_MS: Long = 4_000L

        /** Sane lower bound so a stored/garbage value can never make the slideshow flicker-advance. */
        const val MIN_SLIDESHOW_INTERVAL_MS: Long = 1_000L

        /** Sane upper bound so a stored/garbage value can never pin a slide indefinitely. */
        const val MAX_SLIDESHOW_INTERVAL_MS: Long = 60_000L
    }
}

/** DataStore-backed [ViewDefaults]. Stored enum names / column counts fall back or clamp on read. */
internal class DataStoreViewDefaults(
    private val dataStore: DataStore<Preferences>,
) : ViewDefaults {

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
            (prefs[KEY_SLIDESHOW_MS] ?: ViewDefaults.DEFAULT_SLIDESHOW_INTERVAL_MS)
                .coerceIn(
                    ViewDefaults.MIN_SLIDESHOW_INTERVAL_MS,
                    ViewDefaults.MAX_SLIDESHOW_INTERVAL_MS,
                )
        }

    override suspend fun setSlideshowIntervalMs(ms: Long) {
        dataStore.edit {
            it[KEY_SLIDESHOW_MS] = ms.coerceIn(
                ViewDefaults.MIN_SLIDESHOW_INTERVAL_MS,
                ViewDefaults.MAX_SLIDESHOW_INTERVAL_MS,
            )
        }
    }

    private companion object {
        val KEY_SORT_KEY = stringPreferencesKey("default_sort_key")
        val KEY_SORT_DIR = stringPreferencesKey("default_sort_dir")
        val KEY_COLUMNS = intPreferencesKey("default_columns")
        val KEY_SLIDESHOW_MS = longPreferencesKey("slideshow_interval_ms")
    }
}
