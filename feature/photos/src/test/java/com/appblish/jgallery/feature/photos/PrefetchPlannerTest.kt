package com.appblish.jgallery.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM coverage for the APP-456 cold-cache prefetch index math ([PrefetchPlanner]). */
class PrefetchPlannerTest {

    // --- ahead(): direction, bound, nearest-first, clamping ---

    @Test
    fun `ahead going down is nearest-first past the last visible index`() {
        val plan = PrefetchPlanner.ahead(
            firstVisible = 10, lastVisible = 19, goingDown = true, aheadCount = 4, itemCount = 100,
        )
        assertEquals(listOf(20, 21, 22, 23), plan)
    }

    @Test
    fun `ahead going up is nearest-first below the first visible index`() {
        val plan = PrefetchPlanner.ahead(
            firstVisible = 40, lastVisible = 49, goingDown = false, aheadCount = 3, itemCount = 100,
        )
        // Nearest (39) first, deepest (37) last.
        assertEquals(listOf(39, 38, 37), plan)
    }

    @Test
    fun `ahead clamps to the end of the list going down`() {
        val plan = PrefetchPlanner.ahead(
            firstVisible = 90, lastVisible = 97, goingDown = true, aheadCount = 10, itemCount = 100,
        )
        assertEquals(listOf(98, 99), plan)
    }

    @Test
    fun `ahead clamps to zero going up`() {
        val plan = PrefetchPlanner.ahead(
            firstVisible = 2, lastVisible = 9, goingDown = false, aheadCount = 10, itemCount = 100,
        )
        assertEquals(listOf(1, 0), plan)
    }

    @Test
    fun `ahead never exceeds the bounded count`() {
        val plan = PrefetchPlanner.ahead(
            firstVisible = 0, lastVisible = 9, goingDown = true, aheadCount = 5, itemCount = 10_000,
        )
        assertEquals(5, plan.size)
    }

    @Test
    fun `ahead is empty for degenerate inputs`() {
        assertTrue(PrefetchPlanner.ahead(0, -1, true, 4, 100).isEmpty()) // nothing visible yet
        assertTrue(PrefetchPlanner.ahead(0, 9, true, 0, 100).isEmpty()) // zero window
        assertTrue(PrefetchPlanner.ahead(0, 9, true, 4, 0).isEmpty())   // empty list
    }

    @Test
    fun `ahead at the very end going down yields nothing`() {
        val plan = PrefetchPlanner.ahead(
            firstVisible = 92, lastVisible = 99, goingDown = true, aheadCount = 8, itemCount = 100,
        )
        assertTrue(plan.isEmpty())
    }

    // --- idleWarm(): symmetric, interleaved, clamped, excludes visible ---

    @Test
    fun `idleWarm interleaves below then above nearest-first`() {
        val plan = PrefetchPlanner.idleWarm(
            firstVisible = 20, lastVisible = 29, radius = 3, itemCount = 100,
        )
        // below1, above1, below2, above2, below3, above3
        assertEquals(listOf(30, 19, 31, 18, 32, 17), plan)
    }

    @Test
    fun `idleWarm clamps both ends independently`() {
        val plan = PrefetchPlanner.idleWarm(
            firstVisible = 1, lastVisible = 98, radius = 3, itemCount = 100,
        )
        // below: 99 (then 100,101 out of range); above: 0 (then -1,-2 out of range)
        assertEquals(listOf(99, 0), plan)
    }

    @Test
    fun `idleWarm excludes the visible range`() {
        val plan = PrefetchPlanner.idleWarm(
            firstVisible = 20, lastVisible = 29, radius = 5, itemCount = 100,
        )
        assertTrue(plan.none { it in 20..29 })
    }

    @Test
    fun `idleWarm is empty for degenerate inputs`() {
        assertTrue(PrefetchPlanner.idleWarm(0, -1, 3, 100).isEmpty())
        assertTrue(PrefetchPlanner.idleWarm(0, 9, 0, 100).isEmpty())
        assertTrue(PrefetchPlanner.idleWarm(0, 9, 3, 0).isEmpty())
    }
}
