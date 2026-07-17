package com.appblish.jgallery.core.ui.grid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import com.appblish.jgallery.core.model.ColumnCount
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pinch → column stepping (APP-537): the whole pinch behaviour is now a pure column-count mapping —
 * no graphicsLayer scale, no rubber-band. These lock the live morph thresholds, the 2–6 clamp, and
 * that the reflow spring is critically damped (no bounce), which is the board's core requirement.
 */
class GridZoomTest {

    @Test
    fun `spreading fingers lowers the column count`() {
        assertThat(columnsForPinch(ColumnCount(4), zoom = 1.4f)).isEqualTo(ColumnCount(3))
        assertThat(columnsForPinch(ColumnCount(4), zoom = 2.0f)).isEqualTo(ColumnCount(2))
    }

    @Test
    fun `pinching in raises the column count`() {
        assertThat(columnsForPinch(ColumnCount(3), zoom = 0.7f)).isEqualTo(ColumnCount(4))
        assertThat(columnsForPinch(ColumnCount(3), zoom = 0.55f)).isEqualTo(ColumnCount(5))
    }

    @Test
    fun `small zoom stays on the starting count`() {
        assertThat(columnsForPinch(ColumnCount(3), zoom = 1.1f)).isEqualTo(ColumnCount(3))
        assertThat(columnsForPinch(ColumnCount(3), zoom = 0.95f)).isEqualTo(ColumnCount(3))
    }

    @Test
    fun `count clamps to the 2-6 design range`() {
        assertThat(columnsForPinch(ColumnCount(2), zoom = 10f)).isEqualTo(ColumnCount(2))
        assertThat(columnsForPinch(ColumnCount(6), zoom = 0.1f)).isEqualTo(ColumnCount(6))
    }

    @Test
    fun `stepping is relative to the gesture-start count, not the live count`() {
        // From 6 columns, a steady spread walks down through every count without ratcheting.
        val start = ColumnCount(6)
        assertThat(columnsForPinch(start, 1.35f)).isEqualTo(ColumnCount(4))
        assertThat(columnsForPinch(start, 1.7f)).isEqualTo(ColumnCount(4))
        assertThat(columnsForPinch(start, 2.4f)).isEqualTo(ColumnCount(3))
        assertThat(columnsForPinch(start, 3.0f)).isEqualTo(ColumnCount(2))
    }

    @Test
    fun `identity zoom keeps the count unchanged so a still pinch never reflows`() {
        // The gesture reads columnsForPinch every frame now (live commit); a zoom of exactly 1f must
        // return the start count so simply resting two fingers does not spuriously change columns.
        for (n in ColumnCount.MIN..ColumnCount.MAX) {
            assertThat(columnsForPinch(ColumnCount(n), zoom = 1f)).isEqualTo(ColumnCount(n))
        }
    }

    // --- Reflow spring is clean (APP-537): no bounce/overshoot on the layout transition ---

    @Test
    fun `the reflow placement spring is critically damped so tiles settle with no bounce`() {
        // The board's key ask: the grid must adjust with animation but WITHOUT a bouncy layout. The
        // shared animateItem placement spec must therefore be critically damped (no overshoot).
        val placement = GridReflowPlacementSpec as SpringSpec
        assertThat(placement.dampingRatio).isEqualTo(Spring.DampingRatioNoBouncy)
        assertThat(placement.dampingRatio).isEqualTo(REFLOW_DAMPING)
        assertThat(placement.stiffness).isEqualTo(Spring.StiffnessMediumLow)
    }
}
