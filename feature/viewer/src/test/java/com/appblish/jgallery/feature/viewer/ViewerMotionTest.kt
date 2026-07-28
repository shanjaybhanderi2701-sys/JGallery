package com.appblish.jgallery.feature.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The designer's motion contract (APP-692 `motion-spec` §0/§2.1) as executable spec — the exact numbers
 * the arbiter and the dismiss layer both read. Pure JVM, pixels in.
 */
class ViewerMotionTest {

    // A "typical phone": 800dp tall × ~2.75 density → 2200px. Threshold = clamp(0.15*2200, 96*d, 200*d).
    private val density = 2.75f
    private val containerHeightPx = 2200f
    private val minPx = 96f * density
    private val maxPx = 200f * density

    @Test
    fun `threshold is the 0p15 height fraction, clamped to the 96-200dp band`() {
        // 0.15 * 2200 = 330px; band = [264, 550] → 330 is inside, used as-is.
        assertThat(ViewerMotion.thresholdDistancePx(containerHeightPx, minPx, maxPx)).isWithin(0.01f).of(330f)
        // Tiny container → floored at 96dp. Huge container → capped at 200dp.
        assertThat(ViewerMotion.thresholdDistancePx(100f, minPx, maxPx)).isWithin(0.01f).of(minPx)
        assertThat(ViewerMotion.thresholdDistancePx(100_000f, minPx, maxPx)).isWithin(0.01f).of(maxPx)
    }

    @Test
    fun `dismiss progress p saturates at 1 exactly at the threshold distance`() {
        val threshold = 330f
        assertThat(ViewerMotion.dismissProgress(0f, threshold)).isEqualTo(0f)
        assertThat(ViewerMotion.dismissProgress(165f, threshold)).isWithin(0.001f).of(0.5f)
        assertThat(ViewerMotion.dismissProgress(330f, threshold)).isEqualTo(1f)
        assertThat(ViewerMotion.dismissProgress(9999f, threshold)).isEqualTo(1f)
    }

    @Test
    fun `geometry progress g uses the 0p5 height reference and keeps evolving past threshold`() {
        // g reference = 0.5 * 2200 = 1100px. At the 330px threshold, g = 330/1100 = 0.30 (spec §2.1 table).
        assertThat(ViewerMotion.geometryProgress(330f, containerHeightPx)).isWithin(0.001f).of(0.30f)
        // A half-screen drag (1100px) reaches g = 1.0 — full travel.
        assertThat(ViewerMotion.geometryProgress(1100f, containerHeightPx)).isEqualTo(1f)
    }

    @Test
    fun `visual mappings match the designer table at the threshold g of 0p30`() {
        val g = 0.30f
        // scale ≈ 0.85, photo alpha ≈ 0.91, scrim ≈ 0.70 (motion-spec §2.1 "At threshold" column).
        assertThat(ViewerMotion.pageScale(g)).isWithin(0.005f).of(0.85f)
        assertThat(ViewerMotion.photoAlpha(g)).isWithin(0.005f).of(0.91f)
        assertThat(ViewerMotion.scrimAlpha(g)).isWithin(0.005f).of(0.70f)
    }

    @Test
    fun `at rest all factors are identity, at full travel they hit the specced endpoints`() {
        assertThat(ViewerMotion.pageScale(0f)).isEqualTo(1f)
        assertThat(ViewerMotion.photoAlpha(0f)).isEqualTo(1f)
        assertThat(ViewerMotion.scrimAlpha(0f)).isEqualTo(1f)
        assertThat(ViewerMotion.pageScale(1f)).isEqualTo(ViewerMotion.MinDismissScale) // 0.5
        assertThat(ViewerMotion.photoAlpha(1f)).isEqualTo(ViewerMotion.MinPhotoAlpha) // 0.7
        assertThat(ViewerMotion.scrimAlpha(1f)).isEqualTo(0f) // grid fully revealed
    }
}
