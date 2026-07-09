package com.appblish.jgallery.feature.viewer

import androidx.lifecycle.SavedStateHandle
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.playback.PlaybackSources
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val index = FakeIndex()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(mediaId: String, bucketId: String? = null) = ViewerViewModel(
        repository = index,
        savedStateHandle = SavedStateHandle(
            buildMap {
                put(VIEWER_MEDIA_ID_ARG, mediaId)
                if (bucketId != null) put(VIEWER_BUCKET_ID_ARG, bucketId)
            },
        ),
        playback = UnusedPlayback,
    )

    @Test
    fun `opens on the tapped item`() = runTest {
        index.mediaFlow.value = listOf(item("a"), item("b"), item("c"))
        val vm = viewModel(mediaId = "b")

        val state = vm.uiState.collectReady(this)

        assertThat(state.initialIndex).isEqualTo(1)
        assertThat(state.items).hasSize(3)
    }

    @Test
    fun `launch item already gone falls back to the first item`() = runTest {
        index.mediaFlow.value = listOf(item("a"), item("b"))
        val vm = viewModel(mediaId = "deleted-meanwhile")

        assertThat(vm.uiState.collectReady(this).initialIndex).isEqualTo(0)
    }

    @Test
    fun `initial index freezes across later index updates`() = runTest {
        index.mediaFlow.value = listOf(item("a"), item("b"), item("c"))
        val vm = viewModel(mediaId = "c")
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // An earlier item disappears (deleted externally); "c" is now at position 1, but the
        // frozen launch index must not be recomputed and yank the pager.
        index.mediaFlow.value = listOf(item("b"), item("c"))
        advanceUntilIdle()

        val state = vm.uiState.value as ViewerUiState.Ready
        assertThat(state.items.map { it.id.value }).containsExactly("b", "c").inOrder()
        assertThat(state.initialIndex).isEqualTo(2)
        job.cancel()
    }

    @Test
    fun `empty scope surfaces the empty state`() = runTest {
        index.mediaFlow.value = emptyList()
        val vm = viewModel(mediaId = "a")
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(ViewerUiState.Empty)
        job.cancel()
    }

    @Test
    fun `album launch scopes the pager query to that bucket`() = runTest {
        index.mediaFlow.value = listOf(item("a"))
        val vm = viewModel(mediaId = "a", bucketId = "bucket-7")
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertThat(index.lastQuery?.bucketId).isEqualTo("bucket-7")
        job.cancel()
    }

    @Test
    fun `photos launch pages the whole stream`() = runTest {
        index.mediaFlow.value = listOf(item("a"))
        val vm = viewModel(mediaId = "a")
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertThat(index.lastQuery?.bucketId).isNull()
        job.cancel()
    }

    // --- helpers ---

    private suspend fun kotlinx.coroutines.flow.StateFlow<ViewerUiState>.collectReady(
        scope: TestScope,
    ): ViewerUiState.Ready {
        val job = scope.launch { collect {} }
        scope.advanceUntilIdle()
        val state = value as ViewerUiState.Ready
        job.cancel()
        return state
    }

    private fun item(id: String, type: MediaType = MediaType.IMAGE) = MediaItem(
        id = MediaId(id),
        displayName = "$id.jpg",
        type = type,
        bucketId = "bucket",
        bucketName = "Camera",
        dateTakenMillis = 0L,
        dateModifiedMillis = 0L,
        sizeBytes = 1L,
        width = 100,
        height = 100,
        durationMillis = 0L,
        mimeType = "image/jpeg",
    )

    private class FakeIndex : MediaIndexRepository {
        val mediaFlow = MutableStateFlow<List<MediaItem>>(emptyList())
        var lastQuery: MediaQuery? = null

        override fun observeAlbums(): Flow<List<Album>> = emptyFlow()

        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> {
            lastQuery = query
            return mediaFlow
        }

        override suspend fun refresh() = Unit
    }

    private object UnusedPlayback : PlaybackSources {
        override fun mediaSource(item: MediaItem) = error("not exercised in these tests")
    }
}
