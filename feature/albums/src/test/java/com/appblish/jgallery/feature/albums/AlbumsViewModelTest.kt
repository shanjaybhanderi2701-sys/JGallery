package com.appblish.jgallery.feature.albums

import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Albums tab wiring: Loading → Empty/Content from the index, column persistence round-trip. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts Loading, empty index lands on Empty`() = runTest(dispatcher) {
        val vm = AlbumsViewModel(FakeRepository(), FakePreferences())
        assertThat(vm.state.value).isEqualTo(AlbumsUiState.Loading)
        val state = withTimeout(5_000) { vm.state.first { it != AlbumsUiState.Loading } }
        assertThat(state).isEqualTo(AlbumsUiState.Empty)
    }

    @Test
    fun `albums from the index surface in Content, preserving index order`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = AlbumsViewModel(repository, FakePreferences())

        repository.albums.value = listOf(album("camera", 12), album("screenshots", 4))
        val content = withTimeout(5_000) {
            vm.state.first { it is AlbumsUiState.Content } as AlbumsUiState.Content
        }
        assertThat(content.albums.map { it.bucketId }).containsExactly("camera", "screenshots").inOrder()
        assertThat(content.albums.first().itemCount).isEqualTo(12)
    }

    @Test
    fun `column changes persist and re-emit`() = runTest(dispatcher) {
        val preferences = FakePreferences()
        val vm = AlbumsViewModel(FakeRepository(), preferences)

        vm.setColumns(ColumnCount(2))
        advanceUntilIdle()

        assertThat(preferences.stored.value).isEqualTo(2)
        assertThat(withTimeout(5_000) { vm.columns.first { it.value == 2 } }).isEqualTo(ColumnCount(2))
    }

    private fun album(bucketId: String, count: Int) = Album(
        bucketId = bucketId,
        name = bucketId.replaceFirstChar { it.uppercase() },
        itemCount = count,
        cover = MediaId("$bucketId-cover"),
        newestItemMillis = 1_700_000_000_000,
    )

    private class FakeRepository : MediaIndexRepository {
        val albums = MutableStateFlow<List<Album>>(emptyList())
        override fun observeAlbums(): Flow<List<Album>> = albums
        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> = MutableStateFlow(emptyList())
        override suspend fun refresh() = Unit
    }

    private class FakePreferences : AlbumsPreferences {
        val stored = MutableStateFlow(ColumnCount.DEFAULT.value)
        override val columns: Flow<ColumnCount> = stored.map(ColumnCount::clamp)
        override suspend fun setColumns(columns: ColumnCount) {
            stored.value = columns.value
        }
    }
}
