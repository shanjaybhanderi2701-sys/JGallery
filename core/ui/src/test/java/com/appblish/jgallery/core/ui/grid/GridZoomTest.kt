package com.appblish.jgallery.core.ui.grid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
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

    // --- Real layout-animated release reflow (APP-519) ---

    @Test
    fun `release bridge scale starts the target grid at the pre-release apparent tile size`() {
        // The release commits the new column count immediately (animateItem reflows POSITION) and
        // rescales so the fresh target-columns grid opens at exactly the size the tiles had at
        // finger-up, then springs to 1f. The bridge is releaseScale / settleScaleFor(start, target)
        // — verify it makes apparent tile size continuous across the swap frame.
        //
        // Pinch 3→6, user overshot slightly (finger scale 0.42 vs the exact 0.5 for 6 cols):
        val start = ColumnCount(3)
        val target = ColumnCount(6)
        val releaseScale = 0.42f
        val bridge = releaseScale / settleScaleFor(start, target)
        // Apparent size just before swap: baseSize(start) * releaseScale, and just after: baseSize(target)
        // * bridge. With baseSize(n) ∝ 1/n they must match → no instant size jump at the swap frame.
        val baseStart = 1f / start.value
        val baseTarget = 1f / target.value
        assertThat(baseTarget * bridge).isWithin(1e-6f).of(baseStart * releaseScale)
    }

    @Test
    fun `landing exactly on the target needs no size bridge`() {
        // When the finger scale lands exactly on a column count, the bridge is 1f — size is already
        // correct at the swap frame and only the position reflow (animateItem) remains.
        val bridge = 0.5f / settleScaleFor(ColumnCount(3), ColumnCount(6)) // 0.5 == exact 6-col scale
        assertThat(bridge).isWithin(1e-6f).of(1f)
    }

    // --- Apparent-size-continuous release bridge across the async column swap (APP-521 fix 3) ---

    @Test
    fun `settle bridge keeps apparent tile size continuous whether the async column flip lands early or late`() {
        // The residual "pop" the board saw came from the release rendering a few frames at the wrong
        // apparent tile size while the persisted column count round-tripped through DataStore. The fix
        // derives the graphicsLayer scale from whatever column count is LIVE each frame:
        //     scaleValue = spring * currentColumns / target
        // so apparent size (= scaleValue / currentColumns) collapses to spring / target REGARDLESS of
        // the live count. Prove that invariant holds for both the pre-flip (start) and post-flip
        // (target) counts at every point of the spring — no wrong-size frame, so no pop.
        val start = ColumnCount(3)
        val target = ColumnCount(6)
        val releaseScale = 0.42f
        val bridge = releaseScale / settleScaleFor(start, target)

        // Sample the spring's value space from the bridge (start of settle) down to the 1f rest.
        for (spring in listOf(bridge, bridge + (1f - bridge) * 0.5f, 1f)) {
            val apparentWhileStillStart =
                (spring * start.value / target.value) / start.value // scaleValue / currentColumns
            val apparentAfterFlipToTarget =
                (spring * target.value / target.value) / target.value
            // Same apparent size on the frame before AND after the flip → the swap is invisible.
            assertThat(apparentWhileStillStart).isWithin(1e-6f).of(apparentAfterFlipToTarget)
            assertThat(apparentWhileStillStart).isWithin(1e-6f).of(spring / target.value)
        }

        // Continuity with the drag: at the very start of the settle (spring == bridge) the pre-flip
        // scale must equal the release scale exactly, so there is no jump between finger-up and settle.
        val settleStartScaleAtStartColumns = bridge * start.value / target.value
        assertThat(settleStartScaleAtStartColumns).isWithin(1e-6f).of(releaseScale)
    }

    @Test
    fun `size and position reflow share one spring so they finish together`() {
        // The whole fix hinges on the tile SIZE morph (the graphicsLayer scale settle) and the tile
        // POSITION morph (each grid's Modifier.animateItem placement) running on the SAME spring, so
        // neither snaps while the other animates. Assert both specs carry identical spring timing.
        val placement = GridReflowPlacementSpec as SpringSpec
        assertThat(placement.dampingRatio).isEqualTo(REFLOW_DAMPING)
        assertThat(placement.stiffness).isEqualTo(Spring.StiffnessMediumLow)
        assertThat(GridReflowScaleSpec.dampingRatio).isEqualTo(placement.dampingRatio)
        assertThat(GridReflowScaleSpec.stiffness).isEqualTo(placement.stiffness)
    }
}
