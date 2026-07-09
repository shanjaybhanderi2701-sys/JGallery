package com.appblish.jgallery.feature.photos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.appblish.jgallery.core.model.ColumnCount
import kotlinx.coroutines.flow.Flow
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
}

/** DataStore-backed [PhotosPreferences]. Stored values outside 2–6 are clamped on read. */
internal class DataStorePhotosPreferences(
    private val dataStore: DataStore<Preferences>,
) : PhotosPreferences {

    override val columns: Flow<ColumnCount> =
        dataStore.data.map { prefs ->
            prefs[KEY_COLUMNS]?.let(ColumnCount::clamp) ?: ColumnCount.DEFAULT
        }

    override suspend fun setColumns(columns: ColumnCount) {
        dataStore.edit { it[KEY_COLUMNS] = columns.value }
    }

    private companion object {
        val KEY_COLUMNS = intPreferencesKey("photos_columns")
    }
}
