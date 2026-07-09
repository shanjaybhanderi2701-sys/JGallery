package com.appblish.jgallery.feature.albums

import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

/** Albums tab wiring: Loading → Empty/Content, sort re-order, column + sort persistence, create-album. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeRepository = FakeRepository(),
        operations: FakeOperations = FakeOperations(),
        preferences: FakePreferences = FakePreferences(),
    ) = AlbumsViewModel(repository, operations, preferences)

    @Test
    fun `starts Loading, empty index lands on Empty`() = runTest(dispatcher) {
        val vm = viewModel()
        assertThat(vm.state.value).isEqualTo(AlbumsUiState.Loading)
        val state = withTimeout(5_000) { vm.state.first { it != AlbumsUiState.Loading } }
        assertThat(state).isEqualTo(AlbumsUiState.Empty)
    }

    @Test
    fun `albums from the index surface in Content, default sort keeps newest-first order`() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val vm = viewModel(repository = repository)

            // camera newer than screenshots → default (Last Modified, Desc) puts camera first.
            repository.albums.value = listOf(
                album("screenshots", count = 4, newest = 100),
                album("camera", count = 12, newest = 200),
            )
            val content = withTimeout(5_000) {
                vm.state.first { it is AlbumsUiState.Content } as AlbumsUiState.Content
            }
            assertThat(content.albums.map { it.bucketId }).containsExactly("camera", "screenshots").inOrder()
            assertThat(content.albums.first().itemCount).isEqualTo(12)
        }

    @Test
    fun `sort by File Name Ascending re-orders the album list`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val preferences = FakePreferences()
        val vm = viewModel(repository = repository, preferences = preferences)

        repository.albums.value = listOf(
            album("zeta", count = 1, newest = 300),
            album("alpha", count = 1, newest = 100),
        )
        vm.setSort(SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))
        advanceUntilIdle()

        val content = withTimeout(5_000) {
            vm.state.first {
                it is AlbumsUiState.Content && it.albums.firstOrNull()?.name == "Alpha"
            } as AlbumsUiState.Content
        }
        assertThat(content.albums.map { it.name }).containsExactly("Alpha", "Zeta").inOrder()
    }

    @Test
    fun `sort changes persist and re-emit`() = runTest(dispatcher) {
        val preferences = FakePreferences()
        val vm = viewModel(preferences = preferences)

        vm.setSort(SortSpec(SortKey.FILE_SIZE, SortDirection.ASCENDING))
        advanceUntilIdle()

        assertThat(preferences.storedSort.value).isEqualTo(SortSpec(SortKey.FILE_SIZE, SortDirection.ASCENDING))
        val sort = withTimeout(5_000) { vm.sort.first { it.key == SortKey.FILE_SIZE } }
        assertThat(sort.direction).isEqualTo(SortDirection.ASCENDING)
    }

    @Test
    fun `column changes persist and re-emit`() = runTest(dispatcher) {
        val preferences = FakePreferences()
        val vm = viewModel(preferences = preferences)

        vm.setColumns(ColumnCount(2))
        advanceUntilIdle()

        assertThat(preferences.stored.value).isEqualTo(2)
        assertThat(withTimeout(5_000) { vm.columns.first { it.value == 2 } }).isEqualTo(ColumnCount(2))
    }

    @Test
    fun `createAlbum success emits a Success event`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(operations = operations)

        vm.createAlbum("  Trip 2026  ")
        val event = withTimeout(5_000) { vm.createAlbumEvents.first() }

        assertThat(operations.createdNames).containsExactly("  Trip 2026  ")
        assertThat(event).isInstanceOf(CreateAlbumResult.Success::class.java)
        assertThat((event as CreateAlbumResult.Success).name).isEqualTo("Trip 2026")
    }

    @Test
    fun `createAlbum failure surfaces the reason`() = runTest(dispatcher) {
        val operations = FakeOperations().apply {
            result = OperationResult(
                succeeded = 0, failed = 1,
                failures = listOf(OperationResult.Failure(MediaId("x"), "Album name can't be empty")),
            )
        }
        val vm = viewModel(operations = operations)

        vm.createAlbum("")
        val event = withTimeout(5_000) { vm.createAlbumEvents.first() }

        assertThat(event).isInstanceOf(CreateAlbumResult.Failure::class.java)
        assertThat((event as CreateAlbumResult.Failure).reason).isEqualTo("Album name can't be empty")
    }

    private fun album(bucketId: String, count: Int, newest: Long) = Album(
        bucketId = bucketId,
        name = bucketId.replaceFirstChar { it.uppercase() },
        itemCount = count,
        cover = MediaId("$bucketId-cover"),
        newestItemMillis = newest,
    )

    private class FakeRepository : MediaIndexRepository {
        val albums = MutableStateFlow<List<Album>>(emptyList())
        override fun observeAlbums(): Flow<List<Album>> = albums
        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> = MutableStateFlow(emptyList())
        override suspend fun refresh() = Unit
    }

    private class FakeOperations : MediaOperationsRepository {
        val createdNames = mutableListOf<String>()
        var result: OperationResult = OperationResult(succeeded = 1, failed = 0)
        override suspend fun createAlbum(name: String): OperationResult {
            createdNames += name
            return result
        }
        override fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
        override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
    }

    private class FakePreferences : AlbumsPreferences {
        val stored = MutableStateFlow(ColumnCount.DEFAULT.value)
        val storedSort = MutableStateFlow(SortSpec())
        override val columns: Flow<ColumnCount> = stored.map(ColumnCount::clamp)
        override suspend fun setColumns(columns: ColumnCount) {
            stored.value = columns.value
        }
        override val sort: Flow<SortSpec> = storedSort
        override suspend fun setSort(sort: SortSpec) {
            storedSort.value = sort
        }
    }
}
