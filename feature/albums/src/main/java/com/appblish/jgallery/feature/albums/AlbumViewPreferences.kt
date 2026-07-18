package com.appblish.jgallery.feature.albums

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Whether a per-album view change (Sort / Grid size) applies to **this album only** or to **all
 * albums** (G1-9, design APP-465 TB-03 "this album only vs. all" scope control). The scope is itself
 * persisted per album: an album in [THIS_ALBUM] reads its own override; one left on [ALL_ALBUMS]
 * follows the shared default, so a global tweak flows to every album that hasn't opted out.
 */
enum class ViewScope { THIS_ALBUM, ALL_ALBUMS }

/** The effective in-album view settings for one bucket (design APP-465 TB-03). */
data class AlbumViewSettings(
    val sort: SortSpec = SortSpec(),
    val columns: ColumnCount = ColumnCount.DEFAULT,
    val groupBy: GroupBy = GroupBy.DAY,
    val scope: ViewScope = ViewScope.ALL_ALBUMS,
)

/**
 * Per-album Sort + Grid-size + scope persistence (G1-9). One shared global default plus optional
 * per-bucket overrides live in the same DataStore; [settings] resolves the effective value for a
 * bucket by its scope. Interface so the ViewModel unit-tests against an in-memory fake; the
 * DataStore binding lives in [di.AlbumsModule].
 */
interface AlbumViewPreferences {

    /**
     * Effective settings for [bucketId]: the album's own override when its scope is
     * [ViewScope.THIS_ALBUM], otherwise the shared global default. Falls back to the global value for
     * any field the album has never set.
     */
    fun settings(bucketId: String): Flow<AlbumViewSettings>

    /** Persist [sort] for [bucketId] into the store selected by [scope], recording [scope] on the album. */
    suspend fun setSort(bucketId: String, sort: SortSpec, scope: ViewScope)

    /** Persist [columns] for [bucketId] into the store selected by [scope], recording [scope] on the album. */
    suspend fun setColumns(bucketId: String, columns: ColumnCount, scope: ViewScope)

    /** Persist [groupBy] for [bucketId] into the store selected by [scope], recording [scope] on the album. */
    suspend fun setGroupBy(bucketId: String, groupBy: GroupBy, scope: ViewScope)

    /** Flip which store [bucketId] reads from without changing any value (design TB-03 scope toggle). */
    suspend fun setScope(bucketId: String, scope: ViewScope)
}

/**
 * DataStore-backed [AlbumViewPreferences]. Global keys (`album_sort_*`, `album_columns`) hold the
 * shared default; per-album keys are the global key suffixed with the bucket id. Unknown/legacy
 * stored values fall back to the defaults, and stored column counts are clamped on read.
 *
 * APP-569 seed: when the shared global default has never been set, sort/density seed from the app-wide
 * [ViewDefaults] (the "Default sort" / "Grid density" written in Settings) instead of a hard-coded
 * constant. An explicit global value — or a per-album `THIS_ALBUM` override — still wins, so existing
 * overrides are untouched.
 */
internal class DataStoreAlbumViewPreferences(
    private val dataStore: DataStore<Preferences>,
    private val viewDefaults: ViewDefaults,
) : AlbumViewPreferences {

    override fun settings(bucketId: String): Flow<AlbumViewSettings> =
        combine(
            dataStore.data,
            viewDefaults.defaultSort,
            viewDefaults.defaultColumns,
        ) { prefs, defaultSort, defaultColumns ->
            val scope = prefs.scopeFor(bucketId)
            val global = AlbumViewSettings(
                sort = prefs.sortOrNull(GLOBAL_SORT_KEY, GLOBAL_SORT_DIR) ?: defaultSort,
                columns = prefs.columnsOrNull(GLOBAL_COLUMNS) ?: defaultColumns,
                groupBy = prefs.groupBy(GLOBAL_GROUP),
                scope = scope,
            )
            if (scope == ViewScope.ALL_ALBUMS) {
                global
            } else {
                AlbumViewSettings(
                    // A field the album has never overridden falls back to the global default, so
                    // opting an album into THIS_ALBUM seeds from what it was already showing.
                    sort = prefs.sortOrNull(sortKeyKey(bucketId), sortDirKey(bucketId)) ?: global.sort,
                    columns = prefs.columnsOrNull(columnsKey(bucketId)) ?: global.columns,
                    groupBy = prefs.groupByOrNull(groupKey(bucketId)) ?: global.groupBy,
                    scope = scope,
                )
            }
        }

    override suspend fun setSort(bucketId: String, sort: SortSpec, scope: ViewScope) {
        dataStore.edit { prefs ->
            prefs[scopeKey(bucketId)] = scope.name
            val (keyKey, dirKey) = when (scope) {
                ViewScope.ALL_ALBUMS -> GLOBAL_SORT_KEY to GLOBAL_SORT_DIR
                ViewScope.THIS_ALBUM -> sortKeyKey(bucketId) to sortDirKey(bucketId)
            }
            prefs[keyKey] = sort.key.name
            prefs[dirKey] = sort.direction.name
        }
    }

    override suspend fun setColumns(bucketId: String, columns: ColumnCount, scope: ViewScope) {
        dataStore.edit { prefs ->
            prefs[scopeKey(bucketId)] = scope.name
            val key = when (scope) {
                ViewScope.ALL_ALBUMS -> GLOBAL_COLUMNS
                ViewScope.THIS_ALBUM -> columnsKey(bucketId)
            }
            prefs[key] = columns.value
        }
    }

    override suspend fun setGroupBy(bucketId: String, groupBy: GroupBy, scope: ViewScope) {
        dataStore.edit { prefs ->
            prefs[scopeKey(bucketId)] = scope.name
            val key = when (scope) {
                ViewScope.ALL_ALBUMS -> GLOBAL_GROUP
                ViewScope.THIS_ALBUM -> groupKey(bucketId)
            }
            prefs[key] = groupBy.name
        }
    }

    override suspend fun setScope(bucketId: String, scope: ViewScope) {
        dataStore.edit { it[scopeKey(bucketId)] = scope.name }
    }

    private fun Preferences.scopeFor(bucketId: String): ViewScope =
        this[scopeKey(bucketId)]
            ?.let { name -> ViewScope.entries.firstOrNull { it.name == name } }
            ?: ViewScope.ALL_ALBUMS

    private fun Preferences.sortOrNull(
        keyKey: Preferences.Key<String>,
        dirKey: Preferences.Key<String>,
    ): SortSpec? {
        val key = this[keyKey]?.let { name -> SortKey.entries.firstOrNull { it.name == name } } ?: return null
        val dir = this[dirKey]?.let { name -> SortDirection.entries.firstOrNull { it.name == name } }
            ?: SortSpec().direction
        return SortSpec(key = key, direction = dir)
    }

    private fun Preferences.columnsOrNull(key: Preferences.Key<Int>): ColumnCount? =
        this[key]?.let(ColumnCount::clamp)

    private fun Preferences.groupBy(key: Preferences.Key<String>): GroupBy =
        groupByOrNull(key) ?: GroupBy.DAY

    private fun Preferences.groupByOrNull(key: Preferences.Key<String>): GroupBy? =
        this[key]?.let { name -> GroupBy.entries.firstOrNull { it.name == name } }

    private companion object {
        val GLOBAL_SORT_KEY = stringPreferencesKey("album_view_sort_key")
        val GLOBAL_SORT_DIR = stringPreferencesKey("album_view_sort_dir")
        val GLOBAL_COLUMNS = intPreferencesKey("album_view_columns")
        val GLOBAL_GROUP = stringPreferencesKey("album_view_group")

        fun scopeKey(bucketId: String) = stringPreferencesKey("album_view_scope_$bucketId")
        fun sortKeyKey(bucketId: String) = stringPreferencesKey("album_view_sort_key_$bucketId")
        fun sortDirKey(bucketId: String) = stringPreferencesKey("album_view_sort_dir_$bucketId")
        fun columnsKey(bucketId: String) = intPreferencesKey("album_view_columns_$bucketId")
        fun groupKey(bucketId: String) = stringPreferencesKey("album_view_group_$bucketId")
    }
}
