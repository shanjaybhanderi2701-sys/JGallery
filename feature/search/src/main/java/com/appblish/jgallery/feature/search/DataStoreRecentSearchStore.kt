package com.appblish.jgallery.feature.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appblish.jgallery.feature.search.RecentSearchStore.Companion.MAX_RECENTS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [RecentSearchStore]: the whole history is one [RecentSearchCodec]-encoded string,
 * read/written atomically by DataStore so concurrent records never tear the file. The list is small
 * by construction — capped at [MAX_RECENTS] — and durable across process death.
 */
internal class DataStoreRecentSearchStore(
    private val dataStore: DataStore<Preferences>,
) : RecentSearchStore {

    override val recents: Flow<List<RecentSearch>> =
        dataStore.data.map(::read)

    override suspend fun record(query: RecentSearch) {
        val normalized = query.normalized() ?: return
        dataStore.edit { prefs ->
            val deduped = read(prefs).filterNot { it == normalized }
            val updated = (listOf(normalized) + deduped).take(MAX_RECENTS)
            prefs[KEY] = RecentSearchCodec.encode(updated)
        }
    }

    override suspend fun remove(query: RecentSearch) {
        val normalized = query.normalized() ?: return
        dataStore.edit { prefs ->
            val remaining = read(prefs).filterNot { it == normalized }
            prefs[KEY] = RecentSearchCodec.encode(remaining)
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    /** Decode + defensively re-cap, so a manifest written by a future larger cap can't over-read. */
    private fun read(prefs: Preferences): List<RecentSearch> =
        prefs[KEY]?.let(RecentSearchCodec::decode).orEmpty().take(MAX_RECENTS)

    private companion object {
        val KEY = stringPreferencesKey("recent_searches_v1")
    }
}
