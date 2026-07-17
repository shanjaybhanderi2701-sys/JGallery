package com.appblish.jgallery.core.index

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.appblish.jgallery.core.model.MediaId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FavoritesStoreTest {

    /** In-memory [DataStore] so the store's set/toggle logic is unit-testable on the JVM (no Android). */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }

    private fun store() = DataStoreFavoritesStore(FakePreferencesDataStore())

    @Test
    fun `starts empty`() = runTest {
        assertThat(store().favoriteIds.first()).isEmpty()
    }

    @Test
    fun `setFavorite true adds the id`() = runTest {
        val store = store()
        store.setFavorite(MediaId("a"), true)
        assertThat(store.favoriteIds.first()).containsExactly(MediaId("a"))
    }

    @Test
    fun `setFavorite is idempotent`() = runTest {
        val store = store()
        store.setFavorite(MediaId("a"), true)
        store.setFavorite(MediaId("a"), true)
        assertThat(store.favoriteIds.first()).containsExactly(MediaId("a"))
    }

    @Test
    fun `setFavorite false removes the id and clearing a non-favorite is a no-op`() = runTest {
        val store = store()
        store.setFavorite(MediaId("a"), true)
        store.setFavorite(MediaId("b"), true)
        store.setFavorite(MediaId("a"), false)
        store.setFavorite(MediaId("missing"), false)
        assertThat(store.favoriteIds.first()).containsExactly(MediaId("b"))
    }

    @Test
    fun `toggle flips state both ways`() = runTest {
        val store = store()
        store.toggle(MediaId("a"))
        assertThat(store.favoriteIds.first()).containsExactly(MediaId("a"))
        store.toggle(MediaId("a"))
        assertThat(store.favoriteIds.first()).isEmpty()
    }

    @Test
    fun `isFavorite projects membership`() = runTest {
        val store = store()
        store.setFavorite(MediaId("a"), true)
        assertThat(store.isFavorite(MediaId("a")).first()).isTrue()
        assertThat(store.isFavorite(MediaId("b")).first()).isFalse()
    }

    @Test
    fun `keeps multiple favorites`() = runTest {
        val store = store()
        store.setFavorite(MediaId("a"), true)
        store.setFavorite(MediaId("b"), true)
        store.setFavorite(MediaId("c"), true)
        assertThat(store.favoriteIds.first())
            .containsExactly(MediaId("a"), MediaId("b"), MediaId("c"))
    }
}
