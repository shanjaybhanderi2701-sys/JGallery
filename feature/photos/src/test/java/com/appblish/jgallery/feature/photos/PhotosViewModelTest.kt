package com.appblish.jgallery.feature.photos

import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.OperationResult
import kotlinx.coroutines.flow.emptyFlow
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

/**
 * The Photos tab state machine against fakes of its two seams (index + preferences). Grouping math
 * itself is covered in [PhotoTimelineTest]; here we verify the flow wiring — Loading → Empty/Content,
 * incremental index emissions re-derive the timeline, and column persistence round-trips.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotosViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeRepository = FakeRepository(),
        preferences: FakePreferences = FakePreferences(),
    ) = PhotosViewModel(repository, NoopOperations, preferences, dispatcher)

    @Test
    fun `starts Loading, empty index lands on Empty`() = runTest(dispatcher) {
        val vm = viewModel()
        assertThat(vm.state.value).isEqualTo(PhotosUiState.Loading)
        val state = withTimeout(5_000) { vm.state.first { it != PhotosUiState.Loading } }
        assertThat(state).isEqualTo(PhotosUiState.Empty)
    }

    @Test
    fun `index emission becomes a timeline and incremental updates re-derive it`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = viewModel(repository = repository)

        repository.items.value = listOf(mediaItem("a"), mediaItem("b"))
        val content = withTimeout(5_000) {
            vm.state.first { it is PhotosUiState.Content } as PhotosUiState.Content
        }
        assertThat(content.timeline.itemCount).isEqualTo(2)

        // An incremental index update (E3 upsert) flows straight through to a fresh timeline.
        repository.items.value = listOf(mediaItem("a"), mediaItem("b"), mediaItem("c"))
        val updated = withTimeout(5_000) {
            vm.state.first { it is PhotosUiState.Content && it.timeline.itemCount == 3 }
        }
        assertThat((updated as PhotosUiState.Content).timeline.itemCount).isEqualTo(3)
    }

    @Test
    fun `column changes persist and re-emit`() = runTest(dispatcher) {
        val preferences = FakePreferences()
        val vm = viewModel(preferences = preferences)

        vm.setColumns(ColumnCount(5))
        advanceUntilIdle()

        assertThat(preferences.stored.value).isEqualTo(5)
        assertThat(withTimeout(5_000) { vm.columns.first { it.value == 5 } }).isEqualTo(ColumnCount(5))
    }

    private fun mediaItem(id: String) = MediaItem(
        id = MediaId(id),
        displayName = "$id.jpg",
        type = MediaType.IMAGE,
        bucketId = "b1",
        bucketName = "Camera",
        dateTakenMillis = 1_700_000_000_000,
        dateModifiedMillis = 1_700_000_000_000,
        sizeBytes = 1,
        width = 100,
        height = 100,
        durationMillis = 0,
        mimeType = "image/jpeg",
    )

    private class FakeRepository : MediaIndexRepository {
        val items = MutableStateFlow<List<MediaItem>>(emptyList())
        override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())
        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> = items
        override suspend fun refresh() = Unit
    }

    /** The bulk-op seam is exercised in [com.appblish.jgallery.core.ui.selection] tests; here it is inert. */
    private object NoopOperations : MediaOperationsRepository {
        override suspend fun createAlbum(name: String) = OperationResult(succeeded = 1, failed = 0)
        override suspend fun rename(id: MediaId, newDisplayName: String) = OperationResult(succeeded = 1, failed = 0)
        override suspend fun viewUri(id: MediaId): android.net.Uri? = null
        override fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun copyToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> = emptyFlow()
        override fun moveToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> = emptyFlow()
        override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
        override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
        override suspend fun renameAlbum(bucketId: String, newName: String) = OperationResult(succeeded = 1, failed = 0)
        override fun copyAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun moveAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun deleteAlbum(bucketId: String): Flow<FileOperationEvent> = emptyFlow()
    }

    private class FakePreferences : PhotosPreferences {
        val stored = MutableStateFlow(ColumnCount.DEFAULT.value)
        override val columns: Flow<ColumnCount> = stored.map(ColumnCount::clamp)
        override suspend fun setColumns(columns: ColumnCount) {
            stored.value = columns.value
        }
    }
}
