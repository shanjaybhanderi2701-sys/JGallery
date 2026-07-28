package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.thumbs.WriteBackGauge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * APP-712 regression guard — the P0 thumbnail-loading ceiling fix.
 *
 * These tests assert that [WriteBackGate] keeps the in-flight write-back count ≤ maxInFlight under
 * an N-miss burst (the simulated all-miss fling). If the gate is ever removed or the cap bypassed,
 * the tests fail before the ceiling regression can ship — satisfying the APP-710 process rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteBackGateTest {

    private val cap = 4
    private lateinit var scope: TestScope
    private lateinit var gate: WriteBackGate

    @Before
    fun setUp() {
        WriteBackGauge.reset()
        scope = TestScope(StandardTestDispatcher())
        gate = WriteBackGate(scope, maxInFlight = cap)
    }

    @Test
    fun `cap jobs accepted then drops on saturated gate`() {
        // Launch [cap] jobs that never complete (hold permits forever).
        val latch = Job()
        repeat(cap) { gate.submit { latch.join() } }

        // One more submit while the cap is full must be DROPPED.
        val accepted = gate.submit { /* no-op */ }
        assertFalse("submit past the cap must be dropped", accepted)
    }

    @Test
    fun `inFlight never exceeds cap under N-miss burst`() = scope.runTest {
        val burst = 200
        // All write-backs are instant (just verify the gauge stays bounded through the whole burst).
        var peakObserved = 0
        repeat(burst) { i ->
            gate.submit {
                val live = gate.inFlight()
                if (live > peakObserved) peakObserved = live
            }
            // Observe the in-flight count synchronously between each submit.
            val live = gate.inFlight()
            assertTrue(
                "inFlight=$live after submit $i must be ≤ cap=$cap",
                live <= cap,
            )
        }
        advanceUntilIdle()
        assertEquals(0, gate.inFlight())
        assertTrue("peakObserved=$peakObserved must be ≤ cap=$cap", peakObserved <= cap)
    }

    @Test
    fun `gauge peak matches cap under saturated burst`() = scope.runTest {
        val latch = Job()
        // Fill the gate to the cap and hold permits.
        repeat(cap) { gate.submit { latch.join() } }
        // Drain TestScope's dispatch queue so onEnqueue() increments have fired.
        advanceUntilIdle()
        assertEquals(cap, WriteBackGauge.peakInFlight())

        // Subsequent submits are dropped — gauge.dropped() climbs, inFlight stays at cap.
        val extraDrops = 50
        repeat(extraDrops) { gate.submit { /* would pin a bitmap */ } }
        assertEquals(cap.toLong(), WriteBackGauge.enqueued())
        assertEquals(extraDrops.toLong(), WriteBackGauge.dropped())

        // Complete the latch so all held coroutines finish before the test scope asserts no leaks.
        latch.complete()
        advanceUntilIdle()
    }

    @Test
    fun `permit released after job completes — subsequent submit accepted`() = scope.runTest {
        // Fill to cap.
        val jobs = mutableListOf<Job>()
        val latch = Job()
        repeat(cap) {
            scope.launch { gate.submit { latch.join() } }.also { jobs += it }
        }
        advanceUntilIdle()

        // Next submit must be dropped.
        assertFalse(gate.submit { })

        // Complete all held jobs by completing the latch.
        latch.complete()
        advanceUntilIdle()

        // Now a fresh submit must be accepted (permits were released).
        assertTrue(gate.submit { })
        advanceUntilIdle()
    }
}
