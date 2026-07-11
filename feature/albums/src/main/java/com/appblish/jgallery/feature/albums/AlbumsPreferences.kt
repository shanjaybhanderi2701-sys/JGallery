package com.appblish.jgallery.feature.albums

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Albums-tab view settings, persisted per tab (design §3: density AND sort survive tab switches and
 * process death). Interface so the ViewModel unit-tests with an in-memory fake; the DataStore binding
 * lives in [di.AlbumsModule].
 */
interface AlbumsPreferences {

    /** Grid column count for the Albums tab (2–6, default 3 per design a04). */
    val columns: Flow<ColumnCount>

    suspend fun setColumns(columns: ColumnCount)

    /** Active sort for the Albums tab (spec §6). Default: Last Modified, Descending. */
    val sort: Flow<SortSpec>

    suspend fun setSort(sort: SortSpec)

    /** Bucket ids the user has pinned (spec C4 item 6). Persisted; pinned albums sort to the top. */
    val pinnedBucketIds: Flow<Set<String>>

    /** Pin or unpin [bucketId] (spec C4 item 6). Idempotent. */
    suspend fun setPinned(bucketId: String, pinned: Boolean)
}

/** DataStore-backed [AlbumsPreferences]. Unknown/legacy stored values fall back to the defaults. */
internal class DataStoreAlbumsPreferences(
    private val dataStore: DataStore<Preferences>,
) : AlbumsPreferences {

    override val columns: Flow<ColumnCount> =
        dataStore.data.map { prefs ->
            prefs[KEY_COLUMNS]?.let(ColumnCount::clamp) ?: ColumnCount.DEFAULT
        }

    override suspend fun setColumns(columns: ColumnCount) {
        dataStore.edit { it[KEY_COLUMNS] = columns.value }
    }

    override val sort: Flow<SortSpec> =
        dataStore.data.map { prefs ->
            SortSpec(
                key = prefs[KEY_SORT_KEY].toSortKey(),
                direction = prefs[KEY_SORT_DIR].toSortDirection(),
            )
        }

    override suspend fun setSort(sort: SortSpec) {
        dataStore.edit {
            it[KEY_SORT_KEY] = sort.key.name
            it[KEY_SORT_DIR] = sort.direction.name
        }
    }

    override val pinnedBucketIds: Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[KEY_PINNED].orEmpty() }

    override suspend fun setPinned(bucketId: String, pinned: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_PINNED].orEmpty()
            prefs[KEY_PINNED] = if (pinned) current + bucketId else current - bucketId
        }
    }

    private fun String?.toSortKey(): SortKey =
        this?.let { name -> SortKey.entries.firstOrNull { it.name == name } } ?: DEFAULT.key

    private fun String?.toSortDirection(): SortDirection =
        this?.let { name -> SortDirection.entries.firstOrNull { it.name == name } } ?: DEFAULT.direction

    private companion object {
        val KEY_COLUMNS = intPreferencesKey("albums_columns")
        val KEY_SORT_KEY = stringPreferencesKey("albums_sort_key")
        val KEY_SORT_DIR = stringPreferencesKey("albums_sort_dir")
        val KEY_PINNED = stringSetPreferencesKey("albums_pinned_bucket_ids")
        val DEFAULT = SortSpec()
    }
}
