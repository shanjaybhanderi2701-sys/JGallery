package com.appblish.jgallery.feature.albums

import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.AlbumKind
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.OperationProgress
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.ui.selection.AlbumOpUiState
import com.appblish.jgallery.core.ui.selection.AlbumOpVerb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    fun `albums from the index surface in Content, priority folders lead ordinary folders`() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val vm = viewModel(repository = repository)

            // Camera + Screenshots are priority folders; a synthetic Recent album is surfaced too.
            repository.albums.value = listOf(
                album("screenshots", count = 4, newest = 100),
                album("camera", count = 12, newest = 200),
            )
            val content = withTimeout(5_000) {
                vm.state.first { it is AlbumsUiState.Content } as AlbumsUiState.Content
            }
            // Recent leads; then the priority folders in fixed Camera → Screenshots order (spec C4).
            assertThat(content.albums.first().kind).isEqualTo(AlbumKind.RECENT)
            val folders = content.albums.filter { it.kind == AlbumKind.DEVICE_FOLDER }
            assertThat(folders.map { it.bucketId }).containsExactly("camera", "screenshots").inOrder()
            assertThat(folders.first().itemCount).isEqualTo(12)
        }

    @Test
    fun `sort by File Name Ascending re-orders ordinary folders`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val preferences = FakePreferences()
        val vm = viewModel(repository = repository, preferences = preferences)

        // Non-priority names so the active sort — not the priority tier — decides their order.
        repository.albums.value = listOf(
            album("zeta", count = 1, newest = 300),
            album("alpha", count = 1, newest = 100),
        )
        vm.setSort(SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))
        advanceUntilIdle()

        val content = withTimeout(5_000) {
            vm.state.first {
                it is AlbumsUiState.Content &&
                    it.albums.filter { a -> a.kind == AlbumKind.DEVICE_FOLDER }.firstOrNull()?.name == "Alpha"
            } as AlbumsUiState.Content
        }
        val folders = content.albums.filter { it.kind == AlbumKind.DEVICE_FOLDER }
        assertThat(folders.map { it.name }).containsExactly("Alpha", "Zeta").inOrder()
    }

    @Test
    fun `togglePin persists a pin and floats the album to the very top`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val preferences = FakePreferences()
        val vm = viewModel(repository = repository, preferences = preferences)

        repository.albums.value = listOf(
            album("camera", count = 12, newest = 200),
            album("holiday", count = 3, newest = 50),
        )
        val holiday = withTimeout(5_000) {
            (vm.state.first { it is AlbumsUiState.Content } as AlbumsUiState.Content)
                .albums.first { it.bucketId == "holiday" }
        }
        vm.togglePin(holiday)
        advanceUntilIdle()

        assertThat(preferences.storedPins.value).containsExactly("holiday")
        val content = withTimeout(5_000) {
            vm.state.first {
                it is AlbumsUiState.Content && it.albums.first().bucketId == "holiday"
            } as AlbumsUiState.Content
        }
        // Pinned album outranks even Recent and the priority Camera folder (spec C4 item 6).
        assertThat(content.albums.first().pinned).isTrue()
        assertThat(content.albums.first().bucketId).isEqualTo("holiday")
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

    @Test
    fun `renameAlbum delegates by bucketId and emits Success`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(operations = operations)

        vm.renameAlbum(album("camera", count = 3, newest = 10), "Holiday")
        val event = withTimeout(5_000) { vm.albumActionEvents.first() }

        assertThat(operations.renamed).containsExactly("camera" to "Holiday")
        assertThat(event).isInstanceOf(AlbumActionResult.Success::class.java)
    }

    @Test
    fun `renameAlbum surfaces the failure reason`() = runTest(dispatcher) {
        val operations = FakeOperations().apply {
            result = OperationResult(
                succeeded = 0, failed = 1,
                failures = listOf(OperationResult.Failure(MediaId("camera"), "album no longer exists")),
            )
        }
        val vm = viewModel(operations = operations)

        vm.renameAlbum(album("camera", count = 1, newest = 1), "Whatever")
        val event = withTimeout(5_000) { vm.albumActionEvents.first() }

        assertThat(event).isInstanceOf(AlbumActionResult.Failure::class.java)
        assertThat((event as AlbumActionResult.Failure).reason).isEqualTo("album no longer exists")
    }

    @Test
    fun `copyAlbum streams the bulk op into a clean-success dialog summary`() =
        runTest(dispatcher) {
            val operations = FakeOperations().apply {
                bulkResult = OperationResult(succeeded = 4, failed = 0)
            }
            val vm = viewModel(operations = operations)

            vm.copyAlbum(album("camera", count = 4, newest = 9), destinationBucketId = "shots")
            advanceUntilIdle()

            assertThat(operations.copied).containsExactly("camera" to "shots")
            val state = vm.albumOp.value
            assertThat(state).isInstanceOf(AlbumOpUiState.Finished::class.java)
            state as AlbumOpUiState.Finished
            assertThat(state.context.verb).isEqualTo(AlbumOpVerb.COPY)
            assertThat(state.context.total).isEqualTo(4)
            assertThat(state.cancelled).isFalse()
            assertThat(state.summary.succeeded).isEqualTo(4)
        }

    @Test
    fun `moveAlbum with partial failure resolves to a partial dialog summary`() = runTest(dispatcher) {
        val operations = FakeOperations().apply {
            bulkResult = OperationResult(
                succeeded = 2, failed = 1,
                failures = listOf(OperationResult.Failure(MediaId("7"), "copied, but the source could not be removed")),
            )
        }
        val vm = viewModel(operations = operations)

        vm.moveAlbum(album("camera", count = 3, newest = 9), destinationBucketId = "shots")
        advanceUntilIdle()

        assertThat(operations.moved).containsExactly("camera" to "shots")
        val state = vm.albumOp.value
        assertThat(state).isInstanceOf(AlbumOpUiState.Finished::class.java)
        state as AlbumOpUiState.Finished
        assertThat(state.context.verb).isEqualTo(AlbumOpVerb.MOVE)
        assertThat(state.cancelled).isFalse()
        assertThat(state.summary.failed).isEqualTo(1)
    }

    @Test
    fun `cancelling a running album op yields a cancelled summary from the last progress`() =
        runTest(dispatcher) {
            val operations = FakeOperations().apply {
                // Emit one progress event, then hang so the op is still running when we cancel.
                albumFlow = {
                    flow {
                        emit(
                            FileOperationEvent.InProgress(
                                OperationProgress(completed = 2, total = 5, currentName = "IMG_2.jpg"),
                            ),
                        )
                        awaitCancellation()
                    }
                }
            }
            val vm = viewModel(operations = operations)

            vm.moveAlbum(album("camera", count = 5, newest = 9), destinationBucketId = "shots")
            advanceUntilIdle()
            val running = vm.albumOp.value
            assertThat(running).isInstanceOf(AlbumOpUiState.Running::class.java)
            assertThat((running as AlbumOpUiState.Running).progress?.completed).isEqualTo(2)

            vm.cancelAlbumOp()
            advanceUntilIdle()

            val done = vm.albumOp.value
            assertThat(done).isInstanceOf(AlbumOpUiState.Finished::class.java)
            done as AlbumOpUiState.Finished
            assertThat(done.cancelled).isTrue()
            assertThat(done.summary.succeeded).isEqualTo(2)

            vm.dismissAlbumOp()
            assertThat(vm.albumOp.value).isNull()
        }

    @Test
    fun `deleteAlbum trashes the album members and reports Success`() = runTest(dispatcher) {
        val operations = FakeOperations().apply {
            bulkResult = OperationResult(succeeded = 5, failed = 0)
        }
        val vm = viewModel(operations = operations)

        vm.deleteAlbum(album("camera", count = 5, newest = 9))
        val event = withTimeout(5_000) { vm.albumActionEvents.first() }

        assertThat(operations.deleted).containsExactly("camera")
        assertThat(event).isInstanceOf(AlbumActionResult.Success::class.java)
    }

    // --- Album multi-select (G1-8, APP-467) ------------------------------------------------------

    @Test
    fun `long-press enters selection, toggle adds and removes, clear exits`() = runTest(dispatcher) {
        val vm = viewModel()
        assertThat(vm.albumSelection.value.isActive).isFalse()

        vm.beginAlbumSelection("camera")
        assertThat(vm.albumSelection.value.selected).containsExactly("camera")
        assertThat(vm.albumSelection.value.isActive).isTrue()

        vm.toggleAlbumSelection("shots")
        assertThat(vm.albumSelection.value.selected).containsExactly("camera", "shots")

        vm.toggleAlbumSelection("camera")
        assertThat(vm.albumSelection.value.selected).containsExactly("shots")

        vm.clearAlbumSelection()
        assertThat(vm.albumSelection.value.isActive).isFalse()
    }

    @Test
    fun `drag range-select extends from the anchor and shrinking deselects (item 6)`() = runTest(dispatcher) {
        val vm = viewModel()
        val ordered = listOf("a", "b", "c", "d")

        // Long-press "b" anchors; a drag over "d" selects the inclusive b..d span.
        vm.beginAlbumSelection("b")
        vm.dragOverAlbum("d", ordered)
        assertThat(vm.albumSelection.value.selected).containsExactly("b", "c", "d")

        // Dragging back to "c" shrinks the range — "d" is dropped (nothing was selected before the drag).
        vm.dragOverAlbum("c", ordered)
        assertThat(vm.albumSelection.value.selected).containsExactly("b", "c")
    }

    @Test
    fun `drag range-select unions with the pre-drag selection (item 6)`() = runTest(dispatcher) {
        val vm = viewModel()
        val ordered = listOf("a", "b", "c", "d")

        // Pre-select "a" via a first press, then a second press+drag on c..d must keep "a".
        vm.beginAlbumSelection("a")
        vm.beginAlbumSelection("c")
        vm.dragOverAlbum("d", ordered)
        assertThat(vm.albumSelection.value.selected).containsExactly("a", "c", "d")
    }

    @Test
    fun `deleteSelectedAlbums trashes selected device folders and exits selection`() = runTest(dispatcher) {
        val operations = FakeOperations().apply { bulkResult = OperationResult(succeeded = 3, failed = 0) }
        val vm = viewModel(operations = operations)
        val shown = listOf(album("camera", count = 5, newest = 9), album("shots", count = 3, newest = 8))

        vm.beginAlbumSelection("camera")
        vm.toggleAlbumSelection("shots")
        vm.deleteSelectedAlbums(shown)
        advanceUntilIdle()

        assertThat(operations.deleted).containsExactly("camera", "shots")
        assertThat(vm.albumSelection.value.isActive).isFalse()
        assertThat(withTimeout(5_000) { vm.albumActionEvents.first() })
            .isInstanceOf(AlbumActionResult.Success::class.java)
    }

    @Test
    fun `deleteSelectedAlbums skips smart albums (only real folders are trashable)`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(operations = operations)
        val recent = Album(
            bucketId = AlbumsCatalog.RECENT_BUCKET_ID,
            name = "Recent",
            itemCount = 9,
            cover = MediaId("r"),
            newestItemMillis = 9,
            kind = AlbumKind.RECENT,
        )
        val shown = listOf(recent, album("camera", count = 5, newest = 8))

        vm.selectAllAlbums(shown.map { it.bucketId })
        vm.deleteSelectedAlbums(shown)
        advanceUntilIdle()

        assertThat(operations.deleted).containsExactly("camera")
        assertThat(vm.albumSelection.value.isActive).isFalse()
    }

    @Test
    fun `pinSelectedAlbums pins the whole selection when any is unpinned, then exits`() =
        runTest(dispatcher) {
            val preferences = FakePreferences().apply { storedPins.value = setOf("camera") }
            val vm = viewModel(preferences = preferences)
            val shown = listOf(
                album("camera", count = 5, newest = 9).copy(pinned = true),
                album("shots", count = 3, newest = 8),
            )

            vm.selectAllAlbums(shown.map { it.bucketId })
            vm.pinSelectedAlbums(shown)
            advanceUntilIdle()

            // "shots" was unpinned → pin the whole selection.
            assertThat(preferences.storedPins.value).containsExactly("camera", "shots")
            assertThat(vm.albumSelection.value.isActive).isFalse()
        }

    // --- Multi-select action bar (G1-11, APP-471) ------------------------------------------------

    @Test
    fun `copySelectedAlbums copies every selected folder as one aggregate op and exits selection`() =
        runTest(dispatcher) {
            val operations = FakeOperations().apply { bulkResult = OperationResult(succeeded = 3, failed = 0) }
            val vm = viewModel(operations = operations)
            val shown = listOf(album("camera", count = 5, newest = 9), album("shots", count = 3, newest = 8))

            vm.beginAlbumSelection("camera")
            vm.toggleAlbumSelection("shots")
            vm.copySelectedAlbums(shown, destinationBucketId = "trip")
            advanceUntilIdle()

            assertThat(operations.copied).containsExactly("camera" to "trip", "shots" to "trip")
            assertThat(vm.albumSelection.value.isActive).isFalse()
            val state = vm.albumOp.value
            assertThat(state).isInstanceOf(AlbumOpUiState.Finished::class.java)
            state as AlbumOpUiState.Finished
            assertThat(state.context.verb).isEqualTo(AlbumOpVerb.COPY)
            // One combined op: the total spans both albums and the successes fold together.
            assertThat(state.context.total).isEqualTo(8)
            assertThat(state.context.albumName).isEqualTo("2 albums")
            assertThat(state.summary.succeeded).isEqualTo(6)
        }

    @Test
    fun `moveSelectedAlbums skips smart albums and moves only real folders`() = runTest(dispatcher) {
        val operations = FakeOperations().apply { bulkResult = OperationResult(succeeded = 5, failed = 0) }
        val vm = viewModel(operations = operations)
        val recent = Album(
            bucketId = AlbumsCatalog.RECENT_BUCKET_ID,
            name = "Recent",
            itemCount = 9,
            cover = MediaId("r"),
            newestItemMillis = 9,
            kind = AlbumKind.RECENT,
        )
        val shown = listOf(recent, album("camera", count = 5, newest = 8))

        vm.selectAllAlbums(shown.map { it.bucketId })
        vm.moveSelectedAlbums(shown, destinationBucketId = "trip")
        advanceUntilIdle()

        assertThat(operations.moved).containsExactly("camera" to "trip")
        assertThat(vm.albumSelection.value.isActive).isFalse()
    }

    // --- D4-03: whole-album create-new = FLATTEN (Architect ruling APP-480) -----------------------

    @Test
    fun `copySelectedAlbumsToNew flattens the deduped union of member ids into one new album`() =
        runTest(dispatcher) {
            val operations = FakeOperations().apply { bulkResult = OperationResult(succeeded = 3, failed = 0) }
            val repository = FakeRepository().apply {
                albumMediaByBucket = mapOf(
                    "camera" to listOf(mediaItem("a", "camera"), mediaItem("b", "camera")),
                    // "b" also appears in shots → the union must de-duplicate it (no double-count).
                    "shots" to listOf(mediaItem("c", "shots"), mediaItem("b", "shots")),
                )
            }
            val vm = viewModel(operations = operations, repository = repository)
            val shown = listOf(album("camera", count = 2, newest = 9), album("shots", count = 2, newest = 8))

            vm.beginAlbumSelection("camera")
            vm.toggleAlbumSelection("shots")
            vm.copySelectedAlbumsToNew(shown, name = "Trip")
            advanceUntilIdle()

            // One create-and-fill call, by name, with the deduped union of both folders' member ids (C3).
            assertThat(operations.newAlbumCopies).hasSize(1)
            val (name, ids) = operations.newAlbumCopies.first()
            assertThat(name).isEqualTo("Trip")
            assertThat(ids.map { it.value }).containsExactly("a", "b", "c")
            assertThat(operations.moved).isEmpty()
            assertThat(vm.albumSelection.value.isActive).isFalse()
            val state = vm.albumOp.value
            assertThat(state).isInstanceOf(AlbumOpUiState.Finished::class.java)
            state as AlbumOpUiState.Finished
            assertThat(state.context.verb).isEqualTo(AlbumOpVerb.COPY)
            assertThat(state.context.albumName).isEqualTo("2 albums")
            assertThat(state.summary.succeeded).isEqualTo(3)
        }

    @Test
    fun `moveSelectedAlbumsToNew with only empty sources creates nothing and reports a no-op`() =
        runTest(dispatcher) {
            val operations = FakeOperations()
            val repository = FakeRepository().apply {
                albumMediaByBucket = mapOf("camera" to emptyList(), "shots" to emptyList())
            }
            val vm = viewModel(operations = operations, repository = repository)
            val shown = listOf(album("camera", count = 0, newest = 9), album("shots", count = 0, newest = 8))

            vm.selectAllAlbums(shown.map { it.bucketId })
            vm.moveSelectedAlbumsToNew(shown, name = "Empty")
            advanceUntilIdle()

            // C5: an all-empty selection never creates an album — the seam isn't called, summary is 0/0.
            assertThat(operations.newAlbumMoves).isEmpty()
            val state = vm.albumOp.value
            assertThat(state).isInstanceOf(AlbumOpUiState.Finished::class.java)
            state as AlbumOpUiState.Finished
            assertThat(state.summary.succeeded).isEqualTo(0)
            assertThat(state.summary.failed).isEqualTo(0)
        }

    @Test
    fun `setAlbumCover persists the choice and overrides the index cover in Content`() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val preferences = FakePreferences()
            val vm = viewModel(repository = repository, preferences = preferences)
            repository.albums.value = listOf(album("camera", count = 5, newest = 9))

            // Enter selection then set a new cover.
            vm.beginAlbumSelection("camera")
            vm.setAlbumCover("camera", MediaId("chosen"))
            advanceUntilIdle()

            assertThat(preferences.storedCovers.value).containsEntry("camera", MediaId("chosen"))
            assertThat(vm.albumSelection.value.isActive).isFalse()
            val content = withTimeout(5_000) {
                vm.state.first {
                    it is AlbumsUiState.Content &&
                        it.albums.any { a -> a.bucketId == "camera" && a.cover == MediaId("chosen") }
                } as AlbumsUiState.Content
            }
            assertThat(content.albums.first { it.bucketId == "camera" }.cover).isEqualTo(MediaId("chosen"))
        }

    @Test
    fun `albumMedia queries the selected album bucket for the cover picker`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            albumMediaResult = listOf(mediaItem("m1", "camera"), mediaItem("m2", "camera"))
        }
        val vm = viewModel(repository = repository)

        val media = withTimeout(5_000) { vm.albumMedia("camera").first() }
        assertThat(media.map { it.id.value }).containsExactly("m1", "m2").inOrder()
        assertThat(repository.queriedBuckets).contains("camera")
    }

    private fun mediaItem(id: String, bucketId: String) = MediaItem(
        id = MediaId(id),
        displayName = id,
        type = com.appblish.jgallery.core.model.MediaType.IMAGE,
        bucketId = bucketId,
        bucketName = bucketId,
        dateTakenMillis = 1,
        dateModifiedMillis = 1,
        sizeBytes = 1,
        width = 1,
        height = 1,
        durationMillis = 0,
        mimeType = "image/jpeg",
    )

    private fun album(bucketId: String, count: Int, newest: Long) = Album(
        bucketId = bucketId,
        name = bucketId.replaceFirstChar { it.uppercase() },
        itemCount = count,
        cover = MediaId("$bucketId-cover"),
        newestItemMillis = newest,
    )

    private class FakeRepository : MediaIndexRepository {
        val albums = MutableStateFlow<List<Album>>(emptyList())
        /** Media returned for a per-album (bucket-scoped) query — powers the "Set as cover" picker. */
        var albumMediaResult: List<MediaItem> = emptyList()
        /** Per-bucket media, used by the D4-03 flatten path to expand each folder to its member ids. */
        var albumMediaByBucket: Map<String, List<MediaItem>> = emptyMap()
        val queriedBuckets = mutableListOf<String?>()
        override fun observeAlbums(): Flow<List<Album>> = albums
        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> {
            queriedBuckets += query.bucketId
            val bucket = query.bucketId ?: return MutableStateFlow(emptyList())
            val media = albumMediaByBucket[bucket] ?: albumMediaResult
            return MutableStateFlow(media)
        }
        override suspend fun refresh() = Unit
    }

    private class FakeOperations : MediaOperationsRepository {
        val createdNames = mutableListOf<String>()
        var result: OperationResult = OperationResult(succeeded = 1, failed = 0)
        override suspend fun createAlbum(name: String): OperationResult {
            createdNames += name
            return result
        }
        override suspend fun rename(id: MediaId, newDisplayName: String) = result
        override suspend fun viewUri(id: MediaId): android.net.Uri? = null
        override fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun exportCopy(ids: List<MediaId>, treeUri: android.net.Uri): Flow<FileOperationEvent> = emptyFlow()
        /** Records the (name, ids) union handed to the D4-03 flatten create-new path. */
        val newAlbumCopies = mutableListOf<Pair<String, List<MediaId>>>()
        val newAlbumMoves = mutableListOf<Pair<String, List<MediaId>>>()
        override fun copyToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> {
            newAlbumCopies += name to ids
            return flowOf(FileOperationEvent.Completed(bulkResult))
        }
        override fun moveToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> {
            newAlbumMoves += name to ids
            return flowOf(FileOperationEvent.Completed(bulkResult))
        }
        override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
        override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
        override suspend fun beginCapture(
            albumName: String,
            kind: com.appblish.jgallery.core.model.CaptureKind,
        ): com.appblish.jgallery.core.index.AlbumCapture? = null
        override suspend fun sweepOrphanedCaptures(): Int = 0

        val renamed = mutableListOf<Pair<String, String>>()
        val copied = mutableListOf<Pair<String, String>>()
        val moved = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()
        var bulkResult: OperationResult = OperationResult(succeeded = 1, failed = 0)

        /** Overrides the copy/move album stream (e.g. to emit progress then hang for a cancel test). */
        var albumFlow: (() -> Flow<FileOperationEvent>)? = null

        override suspend fun renameAlbum(bucketId: String, newName: String): OperationResult {
            renamed += bucketId to newName
            return result
        }
        override fun copyAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> {
            copied += bucketId to destinationBucketId
            return albumFlow?.invoke() ?: flowOf(FileOperationEvent.Completed(bulkResult))
        }
        override fun moveAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> {
            moved += bucketId to destinationBucketId
            return albumFlow?.invoke() ?: flowOf(FileOperationEvent.Completed(bulkResult))
        }
        override fun deleteAlbum(bucketId: String): Flow<FileOperationEvent> {
            deleted += bucketId
            return flowOf(FileOperationEvent.Completed(bulkResult))
        }
    }

    private class FakePreferences : AlbumsPreferences {
        val stored = MutableStateFlow(ColumnCount.DEFAULT.value)
        val storedSort = MutableStateFlow(SortSpec())
        val storedPins = MutableStateFlow<Set<String>>(emptySet())
        override val columns: Flow<ColumnCount> = stored.map(ColumnCount::clamp)
        override suspend fun setColumns(columns: ColumnCount) {
            stored.value = columns.value
        }
        override val sort: Flow<SortSpec> = storedSort
        override suspend fun setSort(sort: SortSpec) {
            storedSort.value = sort
        }
        override val pinnedBucketIds: Flow<Set<String>> = storedPins
        override suspend fun setPinned(bucketId: String, pinned: Boolean) {
            storedPins.value = if (pinned) storedPins.value + bucketId else storedPins.value - bucketId
        }
        val storedCovers = MutableStateFlow<Map<String, MediaId>>(emptyMap())
        override val coverOverrides: Flow<Map<String, MediaId>> = storedCovers
        override suspend fun setCover(bucketId: String, cover: MediaId) {
            storedCovers.value = storedCovers.value + (bucketId to cover)
        }
    }
}
