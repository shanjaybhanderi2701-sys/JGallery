package com.appblish.jgallery.core.index

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.appblish.jgallery.core.model.MediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The user's persisted set of favorite media (G2 · APP-543). A single flat set of [MediaId]s — the
 * same shape and DataStore idiom as the Albums-tab pinned-bucket flag — so a star survives process
 * death and every surface (grid tile, full-screen viewer, the Favorites smart view) reads one source
 * of truth. It lives in `:core:index` — not a feature module — because Photos, the Viewer, and Albums
 * all star/unstar the same item and none of them may depend on each other.
 *
 * An interface so ViewModels unit-test against an in-memory fake; the DataStore binding lives in
 * [di.FavoritesModule].
 */
interface FavoritesStore {

    /** The ids the user has favorited, re-emitted on every change. Order is not significant. */
    val favoriteIds: Flow<Set<MediaId>>

    /** True while [id] is favorited — a convenience projection of [favoriteIds] for single-item surfaces. */
    fun isFavorite(id: MediaId): Flow<Boolean>

    /** Star or un-star [id]. Idempotent: starring an already-favorite (or clearing a non-favorite) is a no-op. */
    suspend fun setFavorite(id: MediaId, favorite: Boolean)

    /** Flip [id]'s favorite state. */
    suspend fun toggle(id: MediaId)
}

/**
 * DataStore-backed [FavoritesStore]. Favorites ride a single `Set<String>` preference of raw
 * [MediaId] values — no codec, mirroring `albums_pinned_bucket_ids`. A stored id whose media no
 * longer exists is harmless: the Favorites query simply resolves fewer rows, and re-starring/clearing
 * self-heals the set.
 */
internal class DataStoreFavoritesStore(
    private val dataStore: DataStore<Preferences>,
) : FavoritesStore {

    override val favoriteIds: Flow<Set<MediaId>> =
        dataStore.data.map { prefs -> prefs[KEY_FAVORITES].orEmpty().mapTo(mutableSetOf(), ::MediaId) }

    override fun isFavorite(id: MediaId): Flow<Boolean> =
        dataStore.data.map { prefs -> id.value in prefs[KEY_FAVORITES].orEmpty() }

    override suspend fun setFavorite(id: MediaId, favorite: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES].orEmpty()
            prefs[KEY_FAVORITES] = if (favorite) current + id.value else current - id.value
        }
    }

    override suspend fun toggle(id: MediaId) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES].orEmpty()
            prefs[KEY_FAVORITES] = if (id.value in current) current - id.value else current + id.value
        }
    }

    private companion object {
        val KEY_FAVORITES = stringSetPreferencesKey("favorite_media_ids")
    }
}
