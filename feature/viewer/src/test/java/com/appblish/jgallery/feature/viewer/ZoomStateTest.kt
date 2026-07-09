package com.appblish.jgallery.feature.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The design-§3 gesture-priority rules as executable spec: what gets consumed, how zoom anchors,
 * and how pan clamps to the fitted content. Pure JVM — no Compose runtime host needed because
 * snapshot state reads/writes work outside composition.
 */
class ZoomStateTest {

    private fun state(aspect: Float = 1.5f) = ZoomState().apply {
        containerSize = Size(1200f, 2400f)
        contentAspectRatio = aspect
    }

    // --- Consumption predicate: the single rule that keeps zoom and pager from fighting ---

    @Test
    fun `single pointer at 1x is left to the pager`() {
        assertThat(state().shouldConsume(pointerCount = 1)).isFalse()
    }

    @Test
    fun `pinch is always consumed, even at 1x`() {
        assertThat(state().shouldConsume(pointerCount = 2)).isTrue()
    }

    @Test
    fun `single pointer while zoomed is consumed - pan must not swipe the pager`() {
        val s = state()
        s.transform(centroid = Offset(600f, 1200f), pan = Offset.Zero, zoom = 2f)
        assertThat(s.shouldConsume(pointerCount = 1)).isTrue()
    }

    @Test
    fun `pager re-engages once double-tap returns to 1x`() {
        val s = state()
        s.transform(centroid = Offset(600f, 1200f), pan = Offset.Zero, zoom = 3f)
        val (targetScale, targetOffset) = s.doubleTapTarget(Offset(600f, 1200f))
        assertThat(targetScale).isEqualTo(1f)
        assertThat(targetOffset).isEqualTo(Offset.Zero)
    }

    // --- Zoom mechanics ---

    @Test
    fun `scale clamps to the 1-8x design range`() {
        val s = state()
        s.transform(Offset(600f, 1200f), Offset.Zero, zoom = 100f)
        assertThat(s.scale).isEqualTo(ZoomState.MAX_SCALE)
        s.transform(Offset(600f, 1200f), Offset.Zero, zoom = 0.0001f)
        assertThat(s.scale).isEqualTo(1f)
    }

    @Test
    fun `zooming about the center keeps the offset centered`() {
        val s = state()
        s.transform(centroid = Offset(600f, 1200f), pan = Offset.Zero, zoom = 2f)
        assertThat(s.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun `zoom anchors at the pinch centroid - content under the fingers stays put`() {
        val s = state(aspect = 0.5f) // tall content, fills the 1200x2400 viewport exactly
        val centroid = Offset(900f, 1200f) // right of center
        s.transform(centroid, pan = Offset.Zero, zoom = 2f)
        // Content point under the centroid before: (900-600)/1 = 300 right of center.
        // After 2x it must still be under x=900: offset = 900-600 - 300*2 = -300.
        assertThat(s.offset.x).isWithin(0.001f).of(-300f)
        assertThat(s.offset.y).isWithin(0.001f).of(0f)
    }

    @Test
    fun `double tap at 1x targets 2p5x anchored at the tap point`() {
        val s = state(aspect = 0.5f)
        val (targetScale, targetOffset) = s.doubleTapTarget(tap = Offset(900f, 1200f))
        assertThat(targetScale).isEqualTo(ZoomState.DOUBLE_TAP_SCALE)
        // (center - tap) * (2.5 - 1) = (-300, 0) * 1.5 = (-450, 0)
        assertThat(targetOffset.x).isWithin(0.001f).of(-450f)
        assertThat(targetOffset.y).isWithin(0.001f).of(0f)
    }

    // --- Pan clamping ---

    @Test
    fun `pan while zoomed moves the offset`() {
        val s = state(aspect = 0.5f)
        s.transform(Offset(600f, 1200f), Offset.Zero, zoom = 2f)
        s.transform(Offset(600f, 1200f), pan = Offset(-100f, -50f), zoom = 1f)
        assertThat(s.offset.x).isWithin(0.001f).of(-100f)
        assertThat(s.offset.y).isWithin(0.001f).of(-50f)
    }

    @Test
    fun `pan clamps to the fitted content bounds`() {
        val s = state(aspect = 0.5f) // fitted = full viewport
        s.transform(Offset(600f, 1200f), Offset.Zero, zoom = 2f)
        // At 2x, max overflow per side = (1200*2 - 1200)/2 = 600 horizontally.
        s.transform(Offset(600f, 1200f), pan = Offset(-99_999f, 0f), zoom = 1f)
        assertThat(s.offset.x).isWithin(0.001f).of(-600f)
    }

    @Test
    fun `letterboxed axis stays centered - no panning a fitted image off screen`() {
        // Wide 2:1 content in a 1:2 viewport: fitted height = 600 vs viewport 2400. At 2x the
        // content (1200 tall) still fits vertically, so the y offset must stay locked at 0.
        val s = state(aspect = 2f)
        s.transform(Offset(600f, 1200f), Offset.Zero, zoom = 2f)
        s.transform(Offset(600f, 1200f), pan = Offset(0f, 500f), zoom = 1f)
        assertThat(s.offset.y).isEqualTo(0f)
    }

    @Test
    fun `at 1x offset is pinned to zero`() {
        val s = state()
        s.transform(Offset(600f, 1200f), pan = Offset(300f, 300f), zoom = 1f)
        assertThat(s.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun `reset returns to identity`() {
        val s = state()
        s.transform(Offset(600f, 1200f), Offset(50f, 50f), zoom = 4f)
        s.reset()
        assertThat(s.scale).isEqualTo(1f)
        assertThat(s.offset).isEqualTo(Offset.Zero)
        assertThat(s.isZoomed).isFalse()
    }
}
