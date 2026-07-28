package com.appblish.jgallery.core.thumbs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** JVM coverage for the APP-709 requested-edge histogram — the "instrument first" deliverable. */
class RequestedEdgeHistogramTest {

    @Test
    fun `phone-range requests land only on the 384-768 rungs, never 1024 or 1536`() {
        val hist = RequestedEdgeHistogram()
        // A spread of realistic phone tile edges (2–6 cols on ~1080–1440px content): all ≤ 768.
        listOf(300, 359, 400, 512, 538, 700, 718, 768).forEach { hist.record(it) }

        val snap = hist.snapshot()
        assertThat(snap.total).isEqualTo(8)
        // 300→384, 359→384, 400→512, 512→512, 538→768, 700→768, 718→768, 768→768
        assertThat(snap.byBucket[384]).isEqualTo(2)
        assertThat(snap.byBucket[512]).isEqualTo(2)
        assertThat(snap.byBucket[768]).isEqualTo(4)
        // The large-screen-only rungs must never fire on a phone.
        assertThat(snap.byBucket[1024]).isEqualTo(0)
        assertThat(snap.byBucket[1536]).isEqualTo(0)
        assertThat(snap.oversized).isEqualTo(0)
        assertThat(snap.unbounded).isEqualTo(0)
    }

    @Test
    fun `large-screen edges do fire the upper rungs`() {
        val hist = RequestedEdgeHistogram()
        hist.record(800)  // tablet 2-col → 1024
        hist.record(1280) // tablet-landscape 2-col → 1536

        val snap = hist.snapshot()
        assertThat(snap.byBucket[1024]).isEqualTo(1)
        assertThat(snap.byBucket[1536]).isEqualTo(1)
        assertThat(snap.oversized).isEqualTo(0)
    }

    @Test
    fun `an edge above the top rung is counted and flagged oversized`() {
        val hist = RequestedEdgeHistogram()
        hist.record(2000) // beyond MAX — the real over-decode signal

        val snap = hist.snapshot()
        assertThat(snap.total).isEqualTo(1)
        assertThat(snap.byBucket[ThumbnailSizes.maxEdgePx]).isEqualTo(1) // capped onto the top rung
        assertThat(snap.oversized).isEqualTo(1)
    }

    @Test
    fun `non-positive and explicit unbounded requests count as unbounded`() {
        val hist = RequestedEdgeHistogram()
        hist.record(0)
        hist.record(-5)
        hist.recordUnbounded()

        val snap = hist.snapshot()
        assertThat(snap.total).isEqualTo(3)
        assertThat(snap.unbounded).isEqualTo(3)
        assertThat(snap.byBucket.values.sum()).isEqualTo(0) // nothing bucketed
    }

    @Test
    fun `format prints only non-zero rungs plus the always-on flags`() {
        val hist = RequestedEdgeHistogram()
        hist.record(400) // 512
        hist.record(400) // 512
        hist.record(700) // 768

        val line = hist.snapshot().format()
        assertThat(line).isEqualTo("total=3 512=2 768=1 oversized=0 unbounded=0")
    }

    @Test
    fun `reset clears every tally`() {
        val hist = RequestedEdgeHistogram()
        hist.record(400)
        hist.record(2000)
        hist.recordUnbounded()
        hist.reset()

        val snap = hist.snapshot()
        assertThat(snap.total).isEqualTo(0)
        assertThat(snap.oversized).isEqualTo(0)
        assertThat(snap.unbounded).isEqualTo(0)
        assertThat(snap.byBucket.values.sum()).isEqualTo(0)
    }
}
