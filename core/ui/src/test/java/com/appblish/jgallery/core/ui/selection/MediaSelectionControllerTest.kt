package com.appblish.jgallery.core.ui.selection

import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationProgress
import com.appblish.jgallery.core.model.OperationResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The shared multi-select + bulk-op brain (spec §7.6). Verifies the observable state machine that the
 * Photos and album-detail grids both bind to: progress → summary, selection cleared on success,
 * and the no-op guards (empty selection, copy/move without a destination).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaSelectionControllerTest {

    private fun ids(vararg s: String) = s.map { MediaId(it) }

    private fun controller(
        scope: kotlinx.coroutines.CoroutineScope,
        copy: (List<MediaId>, String) -> Flow<FileOperationEvent> = { _, _ -> emptyFlow() },
        move: (List<MediaId>, String) -> Flow<FileOperationEvent> = { _, _ -> emptyFlow() },
        trash: (List<MediaId>) -> Flow<FileOperationEvent> = { emptyFlow() },
    ) = MediaSelectionController(scope, copy, move, trash)

    @Test fun `copy streams progress then a summary and clears the selection`() = runTest {
        val captured = mutableListOf<Pair<List<MediaId>, String>>()
        val c = controller(
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            copy = { list, dest ->
                captured += list to dest
                flow {
                    emit(FileOperationEvent.InProgress(OperationProgress(1, 2, "a.jpg")))
                    emit(FileOperationEvent.InProgress(OperationProgress(2, 2, "b.jpg")))
                    emit(FileOperationEvent.Completed(OperationResult(succeeded = 2, failed = 0)))
                }
            },
        )
        c.toggle(MediaId("a")); c.toggle(MediaId("b"))
        assertThat(c.selection.value.count).isEqualTo(2)

        c.run(BulkAction.COPY, destinationBucketId = "dest-1")
        advanceUntilIdle()

        assertThat(captured.single()).isEqualTo(ids("a", "b") to "dest-1")
        val finished = c.bulk.value as BulkOperationUiState.Finished
        assertThat(finished.action).isEqualTo(BulkAction.COPY)
        assertThat(finished.summary.succeeded).isEqualTo(2)
        // Selection exits on success so the grid returns to normal browsing.
        assertThat(c.selection.value.isActive).isFalse()
    }

    @Test fun `trash needs no destination and reports failures in the summary`() = runTest {
        val c = controller(
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            trash = {
                flow { emit(FileOperationEvent.Completed(OperationResult(succeeded = 1, failed = 2))) }
            },
        )
        c.toggle(MediaId("x"))
        c.run(BulkAction.TRASH)
        advanceUntilIdle()

        val finished = c.bulk.value as BulkOperationUiState.Finished
        assertThat(finished.summary.failed).isEqualTo(2)
    }

    @Test fun `run is a no-op on an empty selection`() = runTest {
        var called = false
        val c = controller(
            CoroutineScope(StandardTestDispatcher(testScheduler)), trash = { called = true; emptyFlow() })
        c.run(BulkAction.TRASH)
        advanceUntilIdle()
        assertThat(called).isFalse()
        assertThat(c.bulk.value).isEqualTo(BulkOperationUiState.Idle)
    }

    @Test fun `copy without a destination does not run`() = runTest {
        var called = false
        val c = controller(
            CoroutineScope(StandardTestDispatcher(testScheduler)), copy = { _, _ -> called = true; emptyFlow() })
        c.toggle(MediaId("a"))
        c.run(BulkAction.COPY, destinationBucketId = null)
        advanceUntilIdle()
        assertThat(called).isFalse()
        assertThat(c.bulk.value).isEqualTo(BulkOperationUiState.Idle)
    }

    @Test fun `dismissResult returns to Idle`() = runTest {
        val c = controller(
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            trash = { flow { emit(FileOperationEvent.Completed(OperationResult(1, 0))) } },
        )
        c.toggle(MediaId("a"))
        c.run(BulkAction.TRASH)
        advanceUntilIdle()
        assertThat(c.bulk.value).isInstanceOf(BulkOperationUiState.Finished::class.java)
        c.dismissResult()
        assertThat(c.bulk.value).isEqualTo(BulkOperationUiState.Idle)
    }
}
