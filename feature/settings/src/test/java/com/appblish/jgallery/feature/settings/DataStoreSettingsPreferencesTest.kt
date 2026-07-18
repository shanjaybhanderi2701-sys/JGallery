package com.appblish.jgallery.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appblish.jgallery.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Coverage of [DataStoreSettingsPreferences] against an in-memory [DataStore] (design G2 Settings
 * §2/§3): synchronous defaults on an empty store, round-trip of each pref, and that a corrupt/unknown
 * stored enum falls back on read instead of throwing (the "never a spinner, always a sensible default"
 * state model). The default sort + grid density moved to `:core:viewdefaults` (APP-569) — see
 * `DataStoreViewDefaultsTest`.
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
    fun `slideshow interval round-trips`() = runTest {
        prefs.setSlideshowIntervalMs(10_000L)
        assertThat(prefs.slideshowIntervalMs.first()).isEqualTo(10_000L)
    }

    @Test
    fun `unknown stored enum names fall back to defaults`() = runTest {
        backing.edit {
            it[stringPreferencesKey("theme_mode")] = "PLASMA"
        }
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `state written by one instance is read back by a fresh one over the same backing`() = runTest {
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.setSlideshowIntervalMs(7_500L)

        val reopened = DataStoreSettingsPreferences(backing)
        assertThat(reopened.themeMode.first()).isEqualTo(ThemeMode.DARK)
        assertThat(reopened.slideshowIntervalMs.first()).isEqualTo(7_500L)
    }
}
