package com.appblish.jgallery.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM coverage for the fast-fling lookahead gate ([FlingDecodeGate], APP-701 finding #5). */
class FlingDecodeGateTest {

    private val ONE_FRAME_NANOS = 16_000_000L // ~60fps

    // --- smoothVelocity ---

    @Test
    fun `smoothVelocity converts index delta over an interval into items per second`() {
        // 16 items in ~16ms with alpha=1 (take the instant sample) ≈ 1000 items/sec.
        val v = FlingDecodeGate.smoothVelocity(
            prevVelocity = 0f, indexDelta = 16, elapsedNanos = ONE_FRAME_NANOS, alpha = 1f,
        )
        assertEquals(1000f, v, 5f)
    }

    @Test
    fun `smoothVelocity is direction-agnostic`() {
        val down = FlingDecodeGate.smoothVelocity(0f, 20, ONE_FRAME_NANOS, alpha = 1f)
        val up = FlingDecodeGate.smoothVelocity(0f, -20, ONE_FRAME_NANOS, alpha = 1f)
        assertEquals(down, up, 0.001f)
    }

    @Test
    fun `smoothVelocity keeps the previous value on a non-positive interval`() {
        // A duplicate/zero-dt emission must not divide by zero or spike the velocity.
        assertEquals(123f, FlingDecodeGate.smoothVelocity(123f, 50, 0L), 0.001f)
        assertEquals(123f, FlingDecodeGate.smoothVelocity(123f, 50, -5L), 0.001f)
    }

    @Test
    fun `smoothVelocity eases toward the sample rather than jumping`() {
        // alpha=0.5: one big-delta frame only moves halfway, so a single jumpy frame can't trip the gate alone.
        val v = FlingDecodeGate.smoothVelocity(0f, 32, ONE_FRAME_NANOS, alpha = 0.5f)
        assertEquals(1000f, v, 20f) // instant ≈ 2000, smoothed ≈ 1000
    }

    // --- shouldPrefetchAhead: hysteresis ---

    @Test
    fun `steady scroll below both thresholds keeps prefetching`() {
        assertTrue(FlingDecodeGate.shouldPrefetchAhead(velocity = 100f, currentlyPrefetching = true))
        assertTrue(FlingDecodeGate.shouldPrefetchAhead(velocity = 100f, currentlyPrefetching = false))
    }

    @Test
    fun `crossing the suppress threshold stops the lookahead`() {
        assertFalse(
            FlingDecodeGate.shouldPrefetchAhead(
                velocity = FlingDecodeGate.SUPPRESS_ITEMS_PER_SEC + 1f, currentlyPrefetching = true,
            ),
        )
    }

    @Test
    fun `once suppressed it stays suppressed in the hysteresis band`() {
        // Between RESUME and SUPPRESS while already suppressed → still suppressed (no flapping).
        val mid = (FlingDecodeGate.RESUME_ITEMS_PER_SEC + FlingDecodeGate.SUPPRESS_ITEMS_PER_SEC) / 2f
        assertFalse(FlingDecodeGate.shouldPrefetchAhead(velocity = mid, currentlyPrefetching = false))
        // The same velocity while still prefetching would keep prefetching — that's the hysteresis.
        assertTrue(FlingDecodeGate.shouldPrefetchAhead(velocity = mid, currentlyPrefetching = true))
    }

    @Test
    fun `resumes only after dropping below the lower threshold`() {
        assertTrue(
            FlingDecodeGate.shouldPrefetchAhead(
                velocity = FlingDecodeGate.RESUME_ITEMS_PER_SEC - 1f, currentlyPrefetching = false,
            ),
        )
    }

    @Test
    fun `thresholds form a real hysteresis band`() {
        assertTrue(FlingDecodeGate.RESUME_ITEMS_PER_SEC < FlingDecodeGate.SUPPRESS_ITEMS_PER_SEC)
    }
}
