package com.appblish.jgallery.core.ui.selection

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationProgress
import com.appblish.jgallery.core.model.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure text/state derivation behind the whole-album progress dialog (C1-04, item 13). */
class AlbumOpProgressTest {

    private fun context(verb: AlbumOpVerb, total: Int) = AlbumOperationContext(
        verb = verb,
        albumName = "Trips",
        destinationLabel = "Internal storage · Pictures",
        total = total,
        sizeLabel = "1.4 GB",
    )

    @Test
    fun verbForms_swapWithAction() {
        assertEquals("Moving", AlbumOpVerb.MOVE.presentParticiple())
        assertEquals("Copying", AlbumOpVerb.COPY.presentParticiple())
        assertEquals("Moved", AlbumOpVerb.MOVE.pastTense())
        assertEquals("Copied", AlbumOpVerb.COPY.pastTense())
        assertEquals("moved", AlbumOpVerb.MOVE.pastParticiple())
        assertEquals("copied", AlbumOpVerb.COPY.pastParticiple())
    }

    @Test
    fun fractionAndPercent_reflectProgress() {
        val p = OperationProgress(completed = 184, total = 312, currentName = "IMG.jpg")
        assertEquals(59, p.percent()) // 184/312 = 0.589 -> 59%
        assertEquals(0.589f, p.fraction(), 0.001f)
    }

    @Test
    fun fractionAndPercent_areZeroBeforeFirstItem() {
        assertEquals(0f, (null as OperationProgress?).fraction(), 0f)
        assertEquals(0, (null as OperationProgress?).percent())
        val empty = OperationProgress(completed = 0, total = 0, currentName = null)
        assertEquals(0f, empty.fraction(), 0f)
    }

    @Test
    fun runningCounter_readsCompletedOfTotal() {
        val p = OperationProgress(completed = 184, total = 312, currentName = null)
        assertEquals("184 of 312", runningCounter(p, total = 312))
        assertEquals("0 of 312", runningCounter(null, total = 312))
    }

    @Test
    fun finishedHeadline_cleanSuccess_showsCountOnly() {
        val state = AlbumOpUiState.Finished(
            context = context(AlbumOpVerb.MOVE, total = 312),
            summary = OperationResult(succeeded = 312, failed = 0),
        )
        assertEquals("Moved 312", finishedHeadline(state))
    }

    @Test
    fun finishedHeadline_partial_showsXofTotal() {
        val state = AlbumOpUiState.Finished(
            context = context(AlbumOpVerb.MOVE, total = 312),
            summary = OperationResult(
                succeeded = 309,
                failed = 3,
                failures = listOf(OperationResult.Failure(MediaId("a"), "Destination not writable")),
            ),
        )
        assertEquals("Moved 309 of 312", finishedHeadline(state))
    }

    @Test
    fun finishedHeadline_cancelled_showsBeforeCancel() {
        val state = AlbumOpUiState.Finished(
            context = context(AlbumOpVerb.MOVE, total = 312),
            summary = OperationResult(succeeded = 184, failed = 0),
            cancelled = true,
        )
        assertEquals("Moved 184 before cancel", finishedHeadline(state))
    }

    @Test
    fun finishedHeadline_copyVariant_swapsVerbOnly() {
        val state = AlbumOpUiState.Finished(
            context = context(AlbumOpVerb.COPY, total = 50),
            summary = OperationResult(succeeded = 50, failed = 0),
        )
        assertEquals("Copied 50", finishedHeadline(state))
    }
}
