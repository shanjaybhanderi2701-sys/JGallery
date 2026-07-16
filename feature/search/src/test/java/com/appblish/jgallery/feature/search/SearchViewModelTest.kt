package com.appblish.jgallery.feature.search

import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The Search state machine against fakes of its two seams (index + recents). Facet matching itself is
 * covered by [com.appblish.jgallery.core.model] tests; here we verify the wiring — blank query stays
 * Empty, a typed/faceted query debounces into Results or NoResults, and recents record/re-run/remove.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = 1_700_000_000_000L // fixed clock so date facets are deterministic

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeRepository = FakeRepository(),
        store: FakeRecentStore = FakeRecentStore(),
    ) = SearchViewModel(repository, store, dispatcher, { now })

    @Test
    fun `blank query stays Empty and surfaces recents, never the whole library`() = runTest(dispatcher) {
        val repository = FakeRepository(listOf(image("Beach.jpg")))
        val store = FakeRecentStore().apply { seed(RecentSearch("dogs")) }
        val vm = viewModel(repository, store)

        val state = withTimeout(5_000) {
            vm.state.first { it is SearchUiState.Empty && it.recents.isNotEmpty() }
        } as SearchUiState.Empty
        assertThat(state.recents.map { it.text }).containsExactly("dogs")
    }

    @Test
    fun `typing a name debounces into Results with only the matches`() = runTest(dispatcher) {
        val repository = FakeRepository(listOf(image("Beach.jpg"), image("Sunset.jpg")))
        val vm = viewModel(repository)

        vm.setText("beach")
        val results = withTimeout(5_000) {
            vm.state.first { it is SearchUiState.Results } as SearchUiState.Results
        }
        assertThat(results.items.map { it.displayName }).containsExactly("Beach.jpg")
        assertThat(results.text).isEqualTo("beach")
    }

    @Test
    fun `a real query with no match lands on NoResults naming the query`() = runTest(dispatcher) {
        val vm = viewModel(FakeRepository(listOf(image("Beach.jpg"))))

        vm.setText("zzz")
        val state = withTimeout(5_000) {
            vm.state.first { it is SearchUiState.NoResults } as SearchUiState.NoResults
        }
        assertThat(state.text).isEqualTo("zzz")
    }

    @Test
    fun `a media-type chip alone is a non-empty query that filters`() = runTest(dispatcher) {
        val repository = FakeRepository(listOf(image("Beach.jpg"), video("Clip.mp4")))
        val vm = viewModel(repository)

        vm.setMediaType(MediaFilter.VIDEOS)
        val results = withTimeout(5_000) {
            vm.state.first { it is SearchUiState.Results } as SearchUiState.Results
        }
        assertThat(results.items.map { it.displayName }).containsExactly("Clip.mp4")
    }

    @Test
    fun `a date facet narrows to items inside its range`() = runTest(dispatcher) {
        val inYear = image("Recent.jpg", dateTakenMillis = now)
        val lastYear = image("Old.jpg", dateTakenMillis = now - 400L * 86_400_000L)
        val vm = viewModel(FakeRepository(listOf(inYear, lastYear)))

        vm.toggleDate(DateFacet.THIS_YEAR)
        val results = withTimeout(5_000) {
            vm.state.first { it is SearchUiState.Results } as SearchUiState.Results
        }
        assertThat(results.items.map { it.displayName }).containsExactly("Recent.jpg")
        assertThat(results.dateFacet).isEqualTo(DateFacet.THIS_YEAR)
    }

    @Test
    fun `toggling the selected date facet clears it back to the empty state`() = runTest(dispatcher) {
        val vm = viewModel(FakeRepository(listOf(image("Beach.jpg", dateTakenMillis = now))))

        vm.toggleDate(DateFacet.THIS_YEAR)
        withTimeout(5_000) { vm.state.first { it is SearchUiState.Results } }

        vm.toggleDate(DateFacet.THIS_YEAR)
        assertThat(vm.dateFacet.value).isNull()
        withTimeout(5_000) { vm.state.first { it is SearchUiState.Empty } }
    }

    @Test
    fun `recordCurrentQuery persists the whole faceted query, blank text is ignored`() =
        runTest(dispatcher) {
            val store = FakeRecentStore()
            val vm = viewModel(store = store)

            // Blank text ⇒ not recorded (spec: executed non-empty queries only).
            vm.recordCurrentQuery()
            advanceUntilIdle()
            assertThat(store.recents.first()).isEmpty()

            vm.setText("cats")
            vm.setMediaType(MediaFilter.VIDEOS)
            vm.recordCurrentQuery()
            advanceUntilIdle()
            assertThat(store.recents.first())
                .containsExactly(RecentSearch("cats", MediaFilter.VIDEOS))
        }

    @Test
    fun `reRunRecent restores text and both facets`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.reRunRecent(RecentSearch("dogs", MediaFilter.GIFS, dateFacet = "This month"))

        assertThat(vm.text.value).isEqualTo("dogs")
        assertThat(vm.mediaType.value).isEqualTo(MediaFilter.GIFS)
        assertThat(vm.dateFacet.value).isEqualTo(DateFacet.THIS_MONTH)
    }

    @Test
    fun `removeRecent drops one entry and clearRecents wipes all`() = runTest(dispatcher) {
        val store = FakeRecentStore().apply { seed(RecentSearch("a"), RecentSearch("b")) }
        val vm = viewModel(store = store)

        vm.removeRecent(RecentSearch("a"))
        advanceUntilIdle()
        assertThat(store.recents.first().map { it.text }).containsExactly("b")

        vm.clearRecents()
        advanceUntilIdle()
        assertThat(store.recents.first()).isEmpty()
    }

    private fun image(name: String, dateTakenMillis: Long = now) =
        mediaItem(name, MediaType.IMAGE, dateTakenMillis)

    private fun video(name: String, dateTakenMillis: Long = now) =
        mediaItem(name, MediaType.VIDEO, dateTakenMillis)

    private fun mediaItem(name: String, type: MediaType, dateTakenMillis: Long) = MediaItem(
        id = MediaId(name),
        displayName = name,
        type = type,
        bucketId = "b1",
        bucketName = "Camera",
        dateTakenMillis = dateTakenMillis,
        dateModifiedMillis = dateTakenMillis,
        sizeBytes = 1,
        width = 100,
        height = 100,
        durationMillis = if (type == MediaType.VIDEO) 1_000 else 0,
        mimeType = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
    )

    private class FakeRepository(items: List<MediaItem> = emptyList()) : MediaIndexRepository {
        val media = MutableStateFlow(items)
        override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())
        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> = media
        override suspend fun refresh() = Unit
    }

    /** In-memory recents mirroring the store's normalize / dedupe / most-recent-first semantics. */
    private class FakeRecentStore : RecentSearchStore {
        private val state = MutableStateFlow<List<RecentSearch>>(emptyList())
        override val recents: Flow<List<RecentSearch>> = state

        fun seed(vararg entries: RecentSearch) {
            state.value = entries.toList()
        }

        override suspend fun record(query: RecentSearch) {
            val normalized = query.normalized() ?: return
            state.value = (listOf(normalized) + state.value.filterNot { it == normalized })
                .take(RecentSearchStore.MAX_RECENTS)
        }

        override suspend fun remove(query: RecentSearch) {
            val normalized = query.normalized() ?: return
            state.value = state.value.filterNot { it == normalized }
        }

        override suspend fun clear() {
            state.value = emptyList()
        }
    }
}
