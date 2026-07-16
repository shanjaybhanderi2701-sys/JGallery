package com.appblish.jgallery.feature.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.feature.search.RecentSearchStore.Companion.MAX_RECENTS
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * End-to-end coverage of [DataStoreRecentSearchStore] against an in-memory [DataStore]: cap, dedupe,
 * most-recent-first order, blank-query rejection, clear, and that state written by one instance is
 * read back by a fresh one over the same store (the process-death guarantee, sans the file).
 */
class DataStoreRecentSearchStoreTest {

    /** Minimal in-memory Preferences DataStore — no Android, no file, atomic like the real one. */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }

    private val backing = FakePreferencesDataStore()
    private val store = DataStoreRecentSearchStore(backing)

    private suspend fun recents() = store.recents.first()

    @Test
    fun `records newest-first`() = runTest {
        store.record(RecentSearch("first"))
        store.record(RecentSearch("second"))
        store.record(RecentSearch("third"))

        assertThat(recents().map { it.text }).containsExactly("third", "second", "first").inOrder()
    }

    @Test
    fun `re-running a query moves it to the top without duplicating`() = runTest {
        store.record(RecentSearch("a"))
        store.record(RecentSearch("b"))
        store.record(RecentSearch("c"))
        store.record(RecentSearch("a"))

        assertThat(recents().map { it.text }).containsExactly("a", "c", "b").inOrder()
    }

    @Test
    fun `same text under a different facet is a distinct recent`() = runTest {
        store.record(RecentSearch("cats", MediaFilter.ALL))
        store.record(RecentSearch("cats", MediaFilter.VIDEOS))

        assertThat(recents()).containsExactly(
            RecentSearch("cats", MediaFilter.VIDEOS),
            RecentSearch("cats", MediaFilter.ALL),
        ).inOrder()
    }

    @Test
    fun `history is capped at MAX_RECENTS, oldest dropped`() = runTest {
        repeat(MAX_RECENTS + 5) { store.record(RecentSearch("q$it")) }

        val texts = recents().map { it.text }
        assertThat(texts).hasSize(MAX_RECENTS)
        // Newest kept, oldest five (q0..q4) evicted.
        assertThat(texts.first()).isEqualTo("q${MAX_RECENTS + 4}")
        assertThat(texts).doesNotContain("q0")
        assertThat(texts).doesNotContain("q4")
    }

    @Test
    fun `blank and whitespace-only queries are ignored, text is trimmed`() = runTest {
        store.record(RecentSearch(""))
        store.record(RecentSearch("   "))
        store.record(RecentSearch("  hello  "))

        assertThat(recents()).containsExactly(RecentSearch("hello"))
    }

    @Test
    fun `remove drops a single entry, leaving the rest in order`() = runTest {
        store.record(RecentSearch("a"))
        store.record(RecentSearch("b"))
        store.record(RecentSearch("c"))

        store.remove(RecentSearch("b"))

        assertThat(recents().map { it.text }).containsExactly("c", "a").inOrder()
    }

    @Test
    fun `remove matches on the normalized form and is a no-op for an absent entry`() = runTest {
        store.record(RecentSearch("hello"))

        store.remove(RecentSearch("  hello  ")) // normalizes to "hello"
        assertThat(recents()).isEmpty()

        // Removing something that was never there leaves the (now empty) history untouched.
        store.record(RecentSearch("kept"))
        store.remove(RecentSearch("gone"))
        assertThat(recents().map { it.text }).containsExactly("kept")
    }

    @Test
    fun `clear wipes everything`() = runTest {
        store.record(RecentSearch("a"))
        store.record(RecentSearch("b"))

        store.clear()

        assertThat(recents()).isEmpty()
    }

    @Test
    fun `persisted state is read back by a fresh store over the same backing (survives process death)`() =
        runTest {
            store.record(RecentSearch("kept", MediaFilter.GIFS, dateFacet = "2023"))

            val reopened = DataStoreRecentSearchStore(backing)
            assertThat(reopened.recents.first())
                .containsExactly(RecentSearch("kept", MediaFilter.GIFS, dateFacet = "2023"))
        }
}
