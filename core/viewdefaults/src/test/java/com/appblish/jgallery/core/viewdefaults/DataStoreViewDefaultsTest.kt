package com.appblish.jgallery.core.viewdefaults

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Coverage of [DataStoreViewDefaults] against an in-memory [DataStore] (APP-569): synchronous defaults
 * on an empty store, round-trip of each pref, the grid-density clamp, and that a corrupt/unknown stored
 * enum falls back on read instead of throwing.
 */
class DataStoreViewDefaultsTest {

    /** Minimal in-memory Preferences DataStore — no Android, no file, atomic like the real one. */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }

    private val backing = FakePreferencesDataStore()
    private val defaults = DataStoreViewDefaults(backing)

    @Test
    fun `empty store yields the documented defaults`() = runTest {
        assertThat(defaults.defaultSort.first())
            .isEqualTo(SortSpec(SortKey.LAST_MODIFIED, SortDirection.DESCENDING))
        assertThat(defaults.defaultColumns.first()).isEqualTo(ColumnCount.DEFAULT)
    }

    @Test
    fun `default sort round-trips key and direction`() = runTest {
        defaults.setDefaultSort(SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))
        assertThat(defaults.defaultSort.first())
            .isEqualTo(SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))
    }

    @Test
    fun `default columns round-trips a valid value`() = runTest {
        defaults.setDefaultColumns(ColumnCount(5))
        assertThat(defaults.defaultColumns.first()).isEqualTo(ColumnCount(5))
    }

    @Test
    fun `an out-of-range stored column count is clamped on read`() = runTest {
        backing.edit { it[intPreferencesKey("default_columns")] = 99 }
        assertThat(defaults.defaultColumns.first()).isEqualTo(ColumnCount(ColumnCount.MAX))

        backing.edit { it[intPreferencesKey("default_columns")] = 0 }
        assertThat(defaults.defaultColumns.first()).isEqualTo(ColumnCount(ColumnCount.MIN))
    }

    @Test
    fun `unknown stored enum names fall back to defaults`() = runTest {
        backing.edit {
            it[stringPreferencesKey("default_sort_key")] = "BOGUS"
            it[stringPreferencesKey("default_sort_dir")] = "SIDEWAYS"
        }
        assertThat(defaults.defaultSort.first())
            .isEqualTo(SortSpec(SortKey.LAST_MODIFIED, SortDirection.DESCENDING))
    }

    @Test
    fun `state written by one instance is read back by a fresh one over the same backing`() = runTest {
        defaults.setDefaultSort(SortSpec(SortKey.FILE_SIZE, SortDirection.ASCENDING))
        defaults.setDefaultColumns(ColumnCount(4))

        val reopened = DataStoreViewDefaults(backing)
        assertThat(reopened.defaultSort.first())
            .isEqualTo(SortSpec(SortKey.FILE_SIZE, SortDirection.ASCENDING))
        assertThat(reopened.defaultColumns.first()).isEqualTo(ColumnCount(4))
    }
}
