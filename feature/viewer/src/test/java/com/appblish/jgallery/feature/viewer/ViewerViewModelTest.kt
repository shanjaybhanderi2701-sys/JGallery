package com.appblish.jgallery.feature.viewer

import androidx.lifecycle.SavedStateHandle
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.playback.PlaybackSources
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
    private val operations = FakeOperations()

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
        operations = operations,
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

    // --- single-item actions (spec §5/§7) ---

    @Test
    fun `copy runs through the facade and surfaces the done summary`() = runTest {
        operations.result = OperationResult(succeeded = 1, failed = 0)
        val vm = viewModel(mediaId = "a")

        vm.copyTo(MediaId("a"), destinationBucketId = "album-2")
        advanceUntilIdle()

        assertThat(operations.copyCalls).containsExactly(listOf(MediaId("a")) to "album-2")
        val finished = vm.action.value as ViewerActionUiState.Finished
        assertThat(finished.kind).isEqualTo(ViewerActionKind.COPY)
        assertThat(finished.result.failed).isEqualTo(0)
    }

    @Test
    fun `rename surfaces the operation result`() = runTest {
        operations.result = OperationResult(
            succeeded = 0,
            failed = 1,
            failures = listOf(OperationResult.Failure(MediaId("a"), "name already in use")),
        )
        val vm = viewModel(mediaId = "a")

        vm.rename(MediaId("a"), "Sunset.jpg")
        advanceUntilIdle()

        assertThat(operations.renamed).containsExactly(MediaId("a") to "Sunset.jpg")
        val finished = vm.action.value as ViewerActionUiState.Finished
        assertThat(finished.kind).isEqualTo(ViewerActionKind.RENAME)
        assertThat(finished.message()).isEqualTo("Couldn't rename: name already in use")
    }

    @Test
    fun `set as on a vanished item reports it is unavailable, not a broken intent`() = runTest {
        operations.viewUriResult = null // item deleted underneath us
        val vm = viewModel(mediaId = "a")

        vm.setAs(MediaId("a"))
        advanceUntilIdle()

        val finished = vm.action.value as ViewerActionUiState.Finished
        assertThat(finished.kind).isEqualTo(ViewerActionKind.SET_AS)
        assertThat(finished.result.failed).isEqualTo(1)
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

    /** Records mutation calls and echoes a configurable [result]; bulk flows emit one terminal event. */
    private class FakeOperations : MediaOperationsRepository {
        var result: OperationResult = OperationResult(succeeded = 1, failed = 0)
        var viewUriResult: android.net.Uri? = null
        val copyCalls = mutableListOf<Pair<List<MediaId>, String>>()
        val renamed = mutableListOf<Pair<MediaId, String>>()

        override suspend fun createAlbum(name: String) = result
        override suspend fun rename(id: MediaId, newDisplayName: String): OperationResult {
            renamed += id to newDisplayName
            return result
        }
        override suspend fun viewUri(id: MediaId): android.net.Uri? = viewUriResult
        override fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> {
            copyCalls += ids to destinationBucketId
            return flowOf(FileOperationEvent.Completed(result))
        }
        override fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> =
            flowOf(FileOperationEvent.Completed(result))
        override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> =
            flowOf(FileOperationEvent.Completed(result))
        override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> =
            flowOf(FileOperationEvent.Completed(result))
        override suspend fun renameAlbum(bucketId: String, newName: String) = result
        override fun copyAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> =
            flowOf(FileOperationEvent.Completed(result))
        override fun moveAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> =
            flowOf(FileOperationEvent.Completed(result))
        override fun deleteAlbum(bucketId: String): Flow<FileOperationEvent> =
            flowOf(FileOperationEvent.Completed(result))
    }
}
