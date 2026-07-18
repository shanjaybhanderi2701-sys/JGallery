package com.appblish.jgallery.feature.settings

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
import com.appblish.jgallery.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Coverage of [DataStoreSettingsPreferences] against an in-memory [DataStore] (design G2 Settings
 * §2/§3): synchronous defaults on an empty store, round-trip of each pref, the grid-density clamp,
 * and that a corrupt/unknown stored enum falls back on read instead of throwing (the "never a spinner,
 * always a sensible default" state model).
 */
class DataStoreSettingsPreferencesTest {

    /** Minimal in-memory Preferences DataStore — no Android, no file, atomic like the real one. */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }

    private val backing = FakePreferencesDataStore()
    private val prefs = DataStoreSettingsPreferences(backing)

    @Test
    fun `empty store yields the documented defaults`() = runTest {
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
        assertThat(prefs.defaultSort.first())
            .isEqualTo(SortSpec(SortKey.LAST_MODIFIED, SortDirection.DESCENDING))
        assertThat(prefs.defaultColumns.first()).isEqualTo(ColumnCount.DEFAULT)
        assertThat(prefs.slideshowIntervalMs.first())
            .isEqualTo(SettingsPreferences.DEFAULT_SLIDESHOW_INTERVAL_MS)
    }

    @Test
    fun `theme mode round-trips`() = runTest {
        prefs.setThemeMode(ThemeMode.DARK)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.DARK)

        prefs.setThemeMode(ThemeMode.LIGHT)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.LIGHT)
    }

    @Test
    fun `default sort round-trips key and direction`() = runTest {
        prefs.setDefaultSort(SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))
        assertThat(prefs.defaultSort.first())
            .isEqualTo(SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))
    }

    @Test
    fun `default columns round-trips a valid value`() = runTest {
        prefs.setDefaultColumns(ColumnCount(5))
        assertThat(prefs.defaultColumns.first()).isEqualTo(ColumnCount(5))
    }

    @Test
    fun `slideshow interval round-trips`() = runTest {
        prefs.setSlideshowIntervalMs(10_000L)
        assertThat(prefs.slideshowIntervalMs.first()).isEqualTo(10_000L)
    }

    @Test
    fun `an out-of-range stored column count is clamped on read`() = runTest {
        // A value written directly out of band (e.g. a future build with a wider range, or a corrupt
        // store) must not blow up the @JvmInline require(); it is coerced into 2..6.
        backing.edit { it[intPreferencesKey("default_columns")] = 99 }
        assertThat(prefs.defaultColumns.first()).isEqualTo(ColumnCount(ColumnCount.MAX))

        backing.edit { it[intPreferencesKey("default_columns")] = 0 }
        assertThat(prefs.defaultColumns.first()).isEqualTo(ColumnCount(ColumnCount.MIN))
    }

    @Test
    fun `unknown stored enum names fall back to defaults`() = runTest {
        backing.edit {
            it[stringPreferencesKey("theme_mode")] = "PLASMA"
            it[stringPreferencesKey("default_sort_key")] = "BOGUS"
            it[stringPreferencesKey("default_sort_dir")] = "SIDEWAYS"
        }
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
        assertThat(prefs.defaultSort.first())
            .isEqualTo(SortSpec(SortKey.LAST_MODIFIED, SortDirection.DESCENDING))
    }

    @Test
    fun `state written by one instance is read back by a fresh one over the same backing`() = runTest {
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.setDefaultColumns(ColumnCount(4))

        val reopened = DataStoreSettingsPreferences(backing)
        assertThat(reopened.themeMode.first()).isEqualTo(ThemeMode.DARK)
        assertThat(reopened.defaultColumns.first()).isEqualTo(ColumnCount(4))
    }
}
