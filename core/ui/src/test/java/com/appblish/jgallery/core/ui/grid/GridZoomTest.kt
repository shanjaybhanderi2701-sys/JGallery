package com.appblish.jgallery.core.ui.grid

import com.appblish.jgallery.core.model.ColumnCount
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pinch → column stepping (spec §4/§6): live morph thresholds, 2–6 clamp, snap-equals-round. */
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

    // --- Continuous settle math (APP-495) ---

    @Test
    fun `settle scale makes the start grid look exactly like the target grid`() {
        // 4 columns settling to 2 → tiles double → content scaled 2x.
        assertThat(settleScaleFor(ColumnCount(4), ColumnCount(2))).isEqualTo(2f)
        // 3 columns settling to 6 → tiles halve → content scaled 0.5x.
        assertThat(settleScaleFor(ColumnCount(3), ColumnCount(6))).isEqualTo(0.5f)
        // No column change → identity scale, the swap-frame is a no-op.
        assertThat(settleScaleFor(ColumnCount(3), ColumnCount(3))).isEqualTo(1f)
    }

    @Test
    fun `live scale bounds span every reachable column count with a springy overshoot`() {
        // From 3 columns: tightest reaches 6 cols (0.5x), loosest reaches 2 cols (1.5x). Both ends
        // carry a little headroom past the settle scale so the release can bounce back.
        val bounds = pinchScaleBounds(ColumnCount(3))
        assertThat(bounds.start).isLessThan(settleScaleFor(ColumnCount(3), ColumnCount(6)))
        assertThat(bounds.endInclusive).isGreaterThan(settleScaleFor(ColumnCount(3), ColumnCount(2)))
    }
}
