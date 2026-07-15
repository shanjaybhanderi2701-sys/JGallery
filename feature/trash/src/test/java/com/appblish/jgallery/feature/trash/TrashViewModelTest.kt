package com.appblish.jgallery.feature.trash

import com.appblish.jgallery.core.index.TrashRepository
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.TrashEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The Recycle Bin state machine against a fake [TrashRepository]: Loading → Empty/Content, selection
 * math, and that restore / delete / empty delegate correctly and collapse selection. Retention math
 * is covered in `core:model` TrashPolicyTest; the storage policy in `core:storage` TrashEngineTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun entry(id: String, type: MediaType = MediaType.IMAGE) = TrashEntry(
        id = MediaId(id),
        displayName = "$id.jpg",
        type = type,
        mimeType = "image/jpeg",
        originalBucketId = "b-$id",
        originalBucketName = "Camera",
        originalRelativePath = "DCIM/Camera/",
        trashedAtMillis = 1_700_000_000_000L,
        sizeBytes = 10L,
        width = 4,
        height = 3,
        durationMillis = 0L,
    )

    @Test
    fun `empty bin lands on Empty and purges expired items on open`() = runTest(dispatcher) {
        val repo = FakeTrashRepository()
        val vm = TrashViewModel(repo)
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(TrashUiState.Empty)
        assertThat(repo.purgeCalls).isEqualTo(1)
    }

    @Test
    fun `refresh re-purges expired entries, toggles isRefreshing, and ignores re-entrant pulls`() =
        runTest(dispatcher) {
            val repo = FakeTrashRepository()
            val vm = TrashViewModel(repo)
            backgroundScope.launchCollect(vm)
            advanceUntilIdle()
            // init already purged once on open.
            assertThat(repo.purgeCalls).isEqualTo(1)
            assertThat(vm.isRefreshing.value).isFalse()

            val gate = CompletableDeferred<Unit>()
            repo.purgeGate = gate
            vm.refresh()
            advanceUntilIdle()
            // In-flight: the bin re-purge is suspended on the gate, spinner still up.
            assertThat(vm.isRefreshing.value).isTrue()
            assertThat(repo.purgeCalls).isEqualTo(2)

            // A second pull while one is running is a no-op.
            vm.refresh()
            advanceUntilIdle()
            assertThat(repo.purgeCalls).isEqualTo(2)

            gate.complete(Unit)
            advanceUntilIdle()
            assertThat(vm.isRefreshing.value).isFalse()
        }

    @Test
    fun `a populated bin becomes Content`() = runTest(dispatcher) {
        val repo = FakeTrashRepository().apply { entries.value = listOf(entry("1"), entry("2")) }
        val vm = TrashViewModel(repo)
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        val content = vm.state.value as TrashUiState.Content
        assertThat(content.entries).hasSize(2)
        assertThat(content.inSelectionMode).isFalse()
    }

    @Test
    fun `selection toggles, select-all selects everything, and a removed item drops out of selection`() =
        runTest(dispatcher) {
            val repo = FakeTrashRepository().apply { entries.value = listOf(entry("1"), entry("2")) }
            val vm = TrashViewModel(repo)
            backgroundScope.launchCollect(vm)
            advanceUntilIdle()

            vm.toggleSelection(MediaId("1"))
            advanceUntilIdle()
            assertThat((vm.state.value as TrashUiState.Content).selection).containsExactly(MediaId("1"))

            vm.selectAll()
            advanceUntilIdle()
            assertThat((vm.state.value as TrashUiState.Content).allSelected).isTrue()

            // Item "1" leaves the bin (restored elsewhere): it must not linger in the selection set.
            repo.entries.value = listOf(entry("2"))
            advanceUntilIdle()
            assertThat((vm.state.value as TrashUiState.Content).selection).containsExactly(MediaId("2"))
        }

    @Test
    fun `restoreSelected delegates to the repository, clears selection, and publishes a summary`() =
        runTest(dispatcher) {
            val repo = FakeTrashRepository().apply { entries.value = listOf(entry("1"), entry("2")) }
            val vm = TrashViewModel(repo)
            backgroundScope.launchCollect(vm)
            advanceUntilIdle()

            // Restore only item 1, so item 2 remains and the screen stays in Content afterwards.
            vm.toggleSelection(MediaId("1"))
            advanceUntilIdle()
            vm.restoreSelected()
            advanceUntilIdle()

            assertThat(repo.restored).containsExactly(listOf(MediaId("1")))
            assertThat((vm.state.value as TrashUiState.Content).selection).isEmpty()
            assertThat((vm.state.value as TrashUiState.Content).entries.map { it.id })
                .containsExactly(MediaId("2"))
            assertThat(vm.lastSummary.value?.kind).isEqualTo(TrashOpKind.RESTORE)
        }

    @Test
    fun `deleteSelected and emptyBin route to the right repository calls`() = runTest(dispatcher) {
        val repo = FakeTrashRepository().apply { entries.value = listOf(entry("1")) }
        val vm = TrashViewModel(repo)
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.toggleSelection(MediaId("1"))
        advanceUntilIdle()
        vm.deleteSelected()
        advanceUntilIdle()
        assertThat(repo.deleted).containsExactly(listOf(MediaId("1")))

        vm.emptyBin()
        advanceUntilIdle()
        assertThat(repo.emptied).isEqualTo(1)
    }

    @Test
    fun `restoreAll restores every entry`() = runTest(dispatcher) {
        val repo = FakeTrashRepository().apply { entries.value = listOf(entry("1"), entry("2"), entry("3")) }
        val vm = TrashViewModel(repo)
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.restoreAll()
        advanceUntilIdle()
        assertThat(repo.restored.single()).containsExactly(MediaId("1"), MediaId("2"), MediaId("3"))
    }

    private fun CoroutineScope.launchCollect(vm: TrashViewModel) {
        launch { vm.state.collect {} }
    }

    private class FakeTrashRepository : TrashRepository {
        val entries = MutableStateFlow<List<TrashEntry>>(emptyList())
        val restored = mutableListOf<List<MediaId>>()
        val deleted = mutableListOf<List<MediaId>>()
        var emptied = 0
        var purgeCalls = 0

        override fun observeTrash(): Flow<List<TrashEntry>> = entries

        override fun restore(ids: List<MediaId>): Flow<FileOperationEvent> {
            restored += ids
            val drop = ids.toHashSet()
            entries.value = entries.value.filterNot { it.id in drop }
            return flowOf(FileOperationEvent.Completed(OperationResult(ids.size, 0)))
        }

        override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> {
            deleted += ids
            val drop = ids.toHashSet()
            entries.value = entries.value.filterNot { it.id in drop }
            return flowOf(FileOperationEvent.Completed(OperationResult(ids.size, 0)))
        }

        override fun emptyTrash(): Flow<FileOperationEvent> {
            val count = entries.value.size
            emptied++
            entries.value = emptyList()
            return flowOf(FileOperationEvent.Completed(OperationResult(count, 0)))
        }

        /** When set, purgeExpired() suspends on this gate so a test can observe the in-flight window. */
        var purgeGate: CompletableDeferred<Unit>? = null
        override suspend fun purgeExpired(): Int {
            purgeCalls++
            purgeGate?.await()
            return 0
        }
    }
}
