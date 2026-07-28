package com.appblish.jgallery.core.thumbs.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** JVM coverage for the device-tier decode-pool sizing (APP-701, finding #5). */
class DecodePoolSizingTest {

    @Test
    fun `low-ram device gets the low tier regardless of cores or heap`() {
        // A low-RAM flag pins the pool to the minimum even on a big-core, big-heap-reported device.
        assertThat(DecodePoolSizing.parallelism(cores = 8, isLowRam = true, memoryClassMb = 512))
            .isEqualTo(DecodePoolSizing.LOW_TIER)
    }

    @Test
    fun `small heap gets the low tier even without the low-ram flag`() {
        assertThat(DecodePoolSizing.parallelism(cores = 8, isLowRam = false, memoryClassMb = 64))
            .isEqualTo(DecodePoolSizing.LOW_TIER)
        // Boundary: exactly LOW_HEAP_MB still counts as small heap.
        assertThat(
            DecodePoolSizing.parallelism(cores = 8, isLowRam = false, memoryClassMb = DecodePoolSizing.LOW_HEAP_MB),
        ).isEqualTo(DecodePoolSizing.LOW_TIER)
    }

    @Test
    fun `mid heap gets the mid tier`() {
        assertThat(DecodePoolSizing.parallelism(cores = 8, isLowRam = false, memoryClassMb = 128))
            .isEqualTo(DecodePoolSizing.MID_TIER)
        // Just under the high threshold is still mid.
        assertThat(
            DecodePoolSizing.parallelism(cores = 8, isLowRam = false, memoryClassMb = DecodePoolSizing.HIGH_HEAP_MB - 1),
        ).isEqualTo(DecodePoolSizing.MID_TIER)
    }

    @Test
    fun `big heap and many cores unlock the high tier`() {
        assertThat(
            DecodePoolSizing.parallelism(cores = 8, isLowRam = false, memoryClassMb = DecodePoolSizing.HIGH_HEAP_MB),
        ).isEqualTo(DecodePoolSizing.HIGH_TIER)
        assertThat(DecodePoolSizing.parallelism(cores = 8, isLowRam = false, memoryClassMb = 512))
            .isEqualTo(DecodePoolSizing.HIGH_TIER)
    }

    @Test
    fun `pool never exceeds available cores`() {
        // Big heap wants HIGH_TIER (6) but only 4 cores exist → capped at 4.
        assertThat(DecodePoolSizing.parallelism(cores = 4, isLowRam = false, memoryClassMb = 512))
            .isEqualTo(4)
    }

    @Test
    fun `pool never drops below the fling floor`() {
        // 1-core, tiny-heap device still keeps two slots so a fling is never single-threaded.
        assertThat(DecodePoolSizing.parallelism(cores = 1, isLowRam = true, memoryClassMb = 48))
            .isEqualTo(DecodePoolSizing.MIN_PARALLELISM)
        assertThat(DecodePoolSizing.MIN_PARALLELISM).isEqualTo(2)
    }

    // ---- Fetcher pool (APP-709) --------------------------------------------------------------

    @Test
    fun `fetch pool is tiered by heap`() {
        // Low-RAM / small heap → the modest fetch tier.
        assertThat(DecodePoolSizing.fetchParallelism(isLowRam = true, memoryClassMb = 512))
            .isEqualTo(DecodePoolSizing.FETCH_LOW_TIER)
        assertThat(DecodePoolSizing.fetchParallelism(isLowRam = false, memoryClassMb = 64))
            .isEqualTo(DecodePoolSizing.FETCH_LOW_TIER)
        // Boundary: exactly LOW_HEAP_MB still counts as small heap.
        assertThat(DecodePoolSizing.fetchParallelism(isLowRam = false, memoryClassMb = DecodePoolSizing.LOW_HEAP_MB))
            .isEqualTo(DecodePoolSizing.FETCH_LOW_TIER)
        // Mid heap.
        assertThat(DecodePoolSizing.fetchParallelism(isLowRam = false, memoryClassMb = 128))
            .isEqualTo(DecodePoolSizing.FETCH_MID_TIER)
        assertThat(DecodePoolSizing.fetchParallelism(isLowRam = false, memoryClassMb = DecodePoolSizing.HIGH_HEAP_MB - 1))
            .isEqualTo(DecodePoolSizing.FETCH_MID_TIER)
        // Big heap unlocks the widest fetch pool.
        assertThat(DecodePoolSizing.fetchParallelism(isLowRam = false, memoryClassMb = DecodePoolSizing.HIGH_HEAP_MB))
            .isEqualTo(DecodePoolSizing.FETCH_HIGH_TIER)
    }

    @Test
    fun `fetch pool is wider than the decode pool at every tier (cold IO-bound fill)`() {
        // The whole point of the split: cold visible tiles (IO-bound loadThumbnail) get more slots
        // than the heap-heavy decoder at the same device tier.
        assertThat(DecodePoolSizing.FETCH_LOW_TIER).isGreaterThan(DecodePoolSizing.LOW_TIER)
        assertThat(DecodePoolSizing.FETCH_MID_TIER).isGreaterThan(DecodePoolSizing.MID_TIER)
        assertThat(DecodePoolSizing.FETCH_HIGH_TIER).isGreaterThan(DecodePoolSizing.HIGH_TIER)
    }

    @Test
    fun `fetch pool is NOT capped to cores (IO-bound slots park on the thumbnail service)`() {
        // Unlike the decode pool, an IO-bound fetch benefits from more slots than cores.
        assertThat(DecodePoolSizing.fetchParallelism(isLowRam = false, memoryClassMb = 512))
            .isEqualTo(DecodePoolSizing.FETCH_HIGH_TIER)
        assertThat(DecodePoolSizing.FETCH_HIGH_TIER).isGreaterThan(4)
    }
}
