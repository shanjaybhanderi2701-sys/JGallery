package com.appblish.jgallery.core.storage.internal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.TrashEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [TrashMetadataStore]: the whole bin manifest is one [TrashRecordCodec]-encoded
 * string, read/written atomically by DataStore so concurrent trash/restore edits never tear the file.
 * Small by construction — a bin holds tens/hundreds of rows of metadata, not the media bytes.
 *
 * [observe] emits newest-first so the grid matches the W2-09 design (most-recently-trashed first).
 */
internal class DataStoreTrashMetadataStore(
    private val dataStore: DataStore<Preferences>,
) : TrashMetadataStore {

    override fun observe(): Flow<List<TrashEntry>> =
        dataStore.data.map { prefs -> read(prefs).sortedByDescending { it.trashedAtMillis } }

    override suspend fun current(): List<TrashEntry> = read(dataStore.data.first())

    override suspend fun put(entry: TrashEntry) {
        dataStore.edit { prefs ->
            val byId = read(prefs).associateByTo(LinkedHashMap()) { it.id }
            byId[entry.id] = entry
            prefs[KEY] = TrashRecordCodec.encode(byId.values.toList())
        }
    }

    override suspend fun remove(ids: Collection<MediaId>) {
        if (ids.isEmpty()) return
        val drop = ids.toHashSet()
        dataStore.edit { prefs ->
            val kept = read(prefs).filterNot { it.id in drop }
            prefs[KEY] = TrashRecordCodec.encode(kept)
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private fun read(prefs: Preferences): List<TrashEntry> =
        prefs[KEY]?.let(TrashRecordCodec::decode).orEmpty()

    private companion object {
        val KEY = stringPreferencesKey("trash_manifest_v1")
    }
}
