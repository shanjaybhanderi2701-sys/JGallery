package com.appblish.jgallery.feature.albums

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Scope-routing semantics of the real [DataStoreAlbumViewPreferences] (G1-9, APP-468): the effective
 * settings for a bucket resolve to its own override when scoped THIS_ALBUM, otherwise the shared
 * global default — and a global change reaches every album still left on ALL_ALBUMS. Also the APP-569
 * seed: an unset shared default falls back to the app-wide [ViewDefaults].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewPreferencesTest {

    @get:Rule val tmp = TemporaryFolder()

    /** In-memory [ViewDefaults] whose app-wide defaults the test can preset before reading. */
    private class FakeViewDefaults(
        sort: SortSpec = SortSpec(),
        columns: ColumnCount = ColumnCount.DEFAULT,
    ) : ViewDefaults {
        private val sortState = MutableStateFlow(sort)
        private val columnsState = MutableStateFlow(columns)
        override val defaultSort: Flow<SortSpec> = sortState
        override val defaultColumns: Flow<ColumnCount> = columnsState
        override suspend fun setDefaultSort(sort: SortSpec) { sortState.value = sort }
        override suspend fun setDefaultColumns(columns: ColumnCount) { columnsState.value = columns }
    }

    private fun TestScope.newPrefs(
        viewDefaults: ViewDefaults = FakeViewDefaults(),
    ): AlbumViewPreferences {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tmp.newFile("album_view_${counter++}.preferences_pb") },
        )
        return DataStoreAlbumViewPreferences(dataStore, viewDefaults)
    }

    @Test
    fun `default is the global default sort, columns and ALL_ALBUMS scope`() = runTest {
        val prefs = newPrefs()
        val settings = prefs.settings("camera").first()
        assertThat(settings.sort).isEqualTo(SortSpec())
        assertThat(settings.columns).isEqualTo(ColumnCount.DEFAULT)
        assertThat(settings.scope).isEqualTo(ViewScope.ALL_ALBUMS)
    }

    @Test
    fun `THIS_ALBUM sort is scoped to that bucket and leaves other albums on the global default`() =
        runTest {
            val prefs = newPrefs()
            val byNameAsc = SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING)

            prefs.setSort("camera", byNameAsc, ViewScope.THIS_ALBUM)

            assertThat(prefs.settings("camera").first().sort).isEqualTo(byNameAsc)
            assertThat(prefs.settings("camera").first().scope).isEqualTo(ViewScope.THIS_ALBUM)
            // A different album, untouched, still reads the global default.
            assertThat(prefs.settings("screenshots").first().sort).isEqualTo(SortSpec())
        }

    @Test
    fun `ALL_ALBUMS change updates the global default seen by every album on that scope`() = runTest {
        val prefs = newPrefs()
        val bySizeDesc = SortSpec(SortKey.FILE_SIZE, SortDirection.DESCENDING)

        prefs.setSort("camera", bySizeDesc, ViewScope.ALL_ALBUMS)

        assertThat(prefs.settings("camera").first().sort).isEqualTo(bySizeDesc)
        // 'downloads' has no override and is on ALL_ALBUMS → it inherits the new global sort.
        assertThat(prefs.settings("downloads").first().sort).isEqualTo(bySizeDesc)
    }

    @Test
    fun `THIS_ALBUM columns override the global density for that album only`() = runTest {
        val prefs = newPrefs()

        prefs.setColumns("camera", ColumnCount(6), ViewScope.THIS_ALBUM)

        assertThat(prefs.settings("camera").first().columns).isEqualTo(ColumnCount(6))
        assertThat(prefs.settings("screenshots").first().columns).isEqualTo(ColumnCount.DEFAULT)
    }

    @Test
    fun `default group-by is DAY and THIS_ALBUM group override is scoped to that album (APP-499)`() = runTest {
        val prefs = newPrefs()
        assertThat(prefs.settings("camera").first().groupBy).isEqualTo(GroupBy.DAY)

        prefs.setGroupBy("camera", GroupBy.MONTH, ViewScope.THIS_ALBUM)

        assertThat(prefs.settings("camera").first().groupBy).isEqualTo(GroupBy.MONTH)
        // A different album, untouched, still reads the global DAY default.
        assertThat(prefs.settings("screenshots").first().groupBy).isEqualTo(GroupBy.DAY)
    }

    @Test
    fun `ALL_ALBUMS group-by updates the global default seen by every album on that scope (APP-499)`() = runTest {
        val prefs = newPrefs()

        prefs.setGroupBy("camera", GroupBy.YEAR, ViewScope.ALL_ALBUMS)

        assertThat(prefs.settings("camera").first().groupBy).isEqualTo(GroupBy.YEAR)
        assertThat(prefs.settings("downloads").first().groupBy).isEqualTo(GroupBy.YEAR)
    }

    @Test
    fun `toggling scope re-points which store the album reads without losing either value`() = runTest {
        val prefs = newPrefs()
        // Global default sort = by size; this album's private override = by name.
        prefs.setSort("camera", SortSpec(SortKey.FILE_SIZE, SortDirection.DESCENDING), ViewScope.ALL_ALBUMS)
        prefs.setSort("camera", SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING), ViewScope.THIS_ALBUM)

        // On THIS_ALBUM it reads the override…
        assertThat(prefs.settings("camera").first().sort)
            .isEqualTo(SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))

        // …flip to ALL_ALBUMS and it reads the global default again (override retained, not erased).
        prefs.setScope("camera", ViewScope.ALL_ALBUMS)
        assertThat(prefs.settings("camera").first().sort)
            .isEqualTo(SortSpec(SortKey.FILE_SIZE, SortDirection.DESCENDING))

        prefs.setScope("camera", ViewScope.THIS_ALBUM)
        assertThat(prefs.settings("camera").first().sort)
            .isEqualTo(SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))
    }

    @Test
    fun `an unset global default seeds from the app-wide ViewDefaults (APP-569)`() = runTest {
        val seeded = SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING)
        val prefs = newPrefs(FakeViewDefaults(sort = seeded, columns = ColumnCount(5)))

        val settings = prefs.settings("camera").first()
        assertThat(settings.sort).isEqualTo(seeded)
        assertThat(settings.columns).isEqualTo(ColumnCount(5))
    }

    @Test
    fun `an existing global override is untouched by the ViewDefaults seed (APP-569)`() = runTest {
        val prefs = newPrefs(FakeViewDefaults(sort = SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING)))
        val bySizeDesc = SortSpec(SortKey.FILE_SIZE, SortDirection.DESCENDING)

        // Album sets its own global sort; the app-wide seed must not override it.
        prefs.setSort("camera", bySizeDesc, ViewScope.ALL_ALBUMS)

        assertThat(prefs.settings("camera").first().sort).isEqualTo(bySizeDesc)
    }

    private companion object {
        // Each DataStore instance must own a distinct file; a within-test counter keeps them unique.
        var counter = 0
    }
}
