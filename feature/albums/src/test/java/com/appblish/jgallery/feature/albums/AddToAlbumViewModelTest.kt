package com.appblish.jgallery.feature.albums

import androidx.lifecycle.SavedStateHandle
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.OperationResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Wiring for the "Add photos to album" picker: toggle selection then create-and-fill by album name. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddToAlbumViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(operations: FakeOperations = FakeOperations()) =
        AddToAlbumViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ADD_TO_ALBUM_NAME_ARG to "Trip 2026")),
            repository = FakeRepository(),
            operations = operations,
        )

    @Test
    fun `confirm creates-and-fills the album by name and reports the count`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(operations)

        vm.toggle(MediaId("a"))
        vm.toggle(MediaId("b"))
        assertThat(vm.selected.value).containsExactly(MediaId("a"), MediaId("b"))

        vm.confirm()
        val result = withTimeout(5_000) { vm.addEvents.first() }

        assertThat(result).isEqualTo(AddToAlbumResult(added = 2, failed = 0))
        assertThat(operations.newAlbumName).isEqualTo("Trip 2026")
        assertThat(operations.newAlbumIds).containsExactly(MediaId("a"), MediaId("b"))
    }

    @Test
    fun `toggling an already-selected item removes it`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggle(MediaId("a"))
        vm.toggle(MediaId("a"))
        assertThat(vm.selected.value).isEmpty()
    }

    @Test
    fun `confirm with no selection is a no-op`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(operations)
        vm.confirm()
        assertThat(operations.newAlbumIds).isNull()
    }

    private class FakeRepository : MediaIndexRepository {
        override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())
        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> = MutableStateFlow(emptyList())
        override suspend fun refresh() = Unit
    }

    private class FakeOperations : MediaOperationsRepository {
        var newAlbumIds: List<MediaId>? = null
        var newAlbumName: String? = null

        override fun copyToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> {
            newAlbumIds = ids
            newAlbumName = name
            return flow {
                emit(FileOperationEvent.Completed(OperationResult(succeeded = ids.size, failed = 0)))
            }
        }

        override suspend fun createAlbum(name: String) = OperationResult(1, 0)
        override suspend fun rename(id: MediaId, newDisplayName: String) = OperationResult(1, 0)
        override suspend fun viewUri(id: MediaId): android.net.Uri? = null
        override fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun exportCopy(ids: List<MediaId>, treeUri: android.net.Uri): Flow<FileOperationEvent> = emptyFlow()
        override fun moveToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> = emptyFlow()
        override suspend fun beginCapture(
            albumName: String,
            kind: com.appblish.jgallery.core.model.CaptureKind,
        ): com.appblish.jgallery.core.index.AlbumCapture? = null
        override suspend fun sweepOrphanedCaptures(): Int = 0
        override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
        override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
        override suspend fun renameAlbum(bucketId: String, newName: String) = OperationResult(1, 0)
        override fun copyAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun moveAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun deleteAlbum(bucketId: String): Flow<FileOperationEvent> = emptyFlow()
    }
}
