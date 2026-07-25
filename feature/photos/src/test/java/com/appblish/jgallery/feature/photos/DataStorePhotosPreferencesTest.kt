package com.appblish.jgallery.feature.photos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The APP-569 seed on [DataStorePhotosPreferences]: a Photos tab with no per-tab sort/density override
 * reflects the app-wide [ViewDefaults]; once the tab writes its own value that per-tab value wins and
 * later changes to the default no longer move it.
 */
class DataStorePhotosPreferencesTest {

    /** Minimal in-memory Preferences DataStore — no Android, no file, atomic like the real one. */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }

    /** In-memory [ViewDefaults] the test can preset before reading. */
    private class FakeViewDefaults(
        sort: SortSpec = SortSpec(),
        columns: ColumnCount = ColumnCount.DEFAULT,
    ) : ViewDefaults {
        private val sortState = MutableStateFlow(sort)
        private val columnsState = MutableStateFlow(columns)
        private val slideshowState = MutableStateFlow(ViewDefaults.DEFAULT_SLIDESHOW_INTERVAL_MS)
        override val defaultSort: Flow<SortSpec> = sortState
        override val defaultColumns: Flow<ColumnCount> = columnsState
        override val slideshowIntervalMs: Flow<Long> = slideshowState
        override suspend fun setDefaultSort(sort: SortSpec) { sortState.value = sort }
        override suspend fun setDefaultColumns(columns: ColumnCount) { columnsState.value = columns }
        override suspend fun setSlideshowIntervalMs(ms: Long) { slideshowState.value = ms }
    }

    private val backing = FakePreferencesDataStore()

    @Test
    fun `no per-tab override seeds sort and columns from the app-wide default`() = runTest {
        val seededSort = SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING)
        val prefs = DataStorePhotosPreferences(
            backing,
            FakeViewDefaults(sort = seededSort, columns = ColumnCount(6)),
        )

        assertThat(prefs.sort.first()).isEqualTo(seededSort)
        assertThat(prefs.columns.first()).isEqualTo(ColumnCount(6))
    }

    @Test
    fun `a per-tab override wins over the app-wide default and later default changes do not move it`() =
        runTest {
            val viewDefaults = FakeViewDefaults(sort = SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))
            val prefs = DataStorePhotosPreferences(backing, viewDefaults)
            val bySizeDesc = SortSpec(SortKey.FILE_SIZE, SortDirection.DESCENDING)

            prefs.setSort(bySizeDesc)
            prefs.setColumns(ColumnCount(4))

            assertThat(prefs.sort.first()).isEqualTo(bySizeDesc)
            assertThat(prefs.columns.first()).isEqualTo(ColumnCount(4))

            // Changing the app-wide default now must NOT move a tab that already has its own value.
            viewDefaults.setDefaultSort(SortSpec(SortKey.FILE_PATH, SortDirection.ASCENDING))
            viewDefaults.setDefaultColumns(ColumnCount(2))

            assertThat(prefs.sort.first()).isEqualTo(bySizeDesc)
            assertThat(prefs.columns.first()).isEqualTo(ColumnCount(4))
        }
}
