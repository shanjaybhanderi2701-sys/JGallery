package com.appblish.jgallery.feature.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The designer's motion contract (APP-711 `motion-spec` §1/§2.1/§7) as executable spec — the exact numbers
 * the arbiter and the dismiss layer both read. Pure JVM, pixels in. Values here are the APP-711 re-tune of
 * the APP-692 originals (§7 delta table): 0.12·H threshold, 80–160 dp clamp, 0.60 scale floor.
 */
class ViewerMotionTest {

    // A "typical phone": 800dp tall × ~2.75 density → 2200px. Threshold = clamp(0.12*2200, 80*d, 160*d).
    private val density = 2.75f
    private val containerHeightPx = 2200f
    private val minPx = 80f * density // 220px
    private val maxPx = 160f * density // 440px

    @Test
    fun `threshold is the 0p12 height fraction, clamped to the 80-160dp band`() {
        // 0.12 * 2200 = 264px; band = [220, 440] → 264 is inside, used as-is.
        assertThat(ViewerMotion.thresholdDistancePx(containerHeightPx, minPx, maxPx)).isWithin(0.01f).of(264f)
        // Tiny container → floored at 80dp. Huge container → capped at 160dp.
        assertThat(ViewerMotion.thresholdDistancePx(100f, minPx, maxPx)).isWithin(0.01f).of(minPx)
        assertThat(ViewerMotion.thresholdDistancePx(100_000f, minPx, maxPx)).isWithin(0.01f).of(maxPx)
    }

    @Test
    fun `dismiss progress p saturates at 1 exactly at the threshold distance`() {
        val threshold = 264f
        assertThat(ViewerMotion.dismissProgress(0f, threshold)).isEqualTo(0f)
        assertThat(ViewerMotion.dismissProgress(132f, threshold)).isWithin(0.001f).of(0.5f)
        assertThat(ViewerMotion.dismissProgress(264f, threshold)).isEqualTo(1f)
        assertThat(ViewerMotion.dismissProgress(9999f, threshold)).isEqualTo(1f)
    }

    @Test
    fun `geometry progress g uses the 0p5 height reference and keeps evolving past threshold`() {
        // g reference = 0.5 * 2200 = 1100px. At the 264px threshold, g = 264/1100 = 0.24 (spec §2.1 table).
        assertThat(ViewerMotion.geometryProgress(264f, containerHeightPx)).isWithin(0.001f).of(0.24f)
        // A half-screen drag (1100px) reaches g = 1.0 — full travel.
        assertThat(ViewerMotion.geometryProgress(1100f, containerHeightPx)).isEqualTo(1f)
    }

    @Test
    fun `visual mappings match the designer table at the threshold g of 0p24`() {
        val g = 0.24f
        // scale ≈ 0.90, photo alpha ≈ 0.93, scrim ≈ 0.76 (motion-spec §2.1 "At threshold" column, §7 re-tune).
        assertThat(ViewerMotion.pageScale(g)).isWithin(0.005f).of(0.904f)
        assertThat(ViewerMotion.photoAlpha(g)).isWithin(0.005f).of(0.928f)
        assertThat(ViewerMotion.scrimAlpha(g)).isWithin(0.005f).of(0.76f)
    }

    @Test
    fun `at rest all factors are identity, at full travel they hit the specced endpoints`() {
        assertThat(ViewerMotion.pageScale(0f)).isEqualTo(1f)
        assertThat(ViewerMotion.photoAlpha(0f)).isEqualTo(1f)
        assertThat(ViewerMotion.scrimAlpha(0f)).isEqualTo(1f)
        assertThat(ViewerMotion.pageScale(1f)).isEqualTo(ViewerMotion.MinDismissScale) // 0.60
        assertThat(ViewerMotion.photoAlpha(1f)).isEqualTo(ViewerMotion.MinPhotoAlpha) // 0.70
        assertThat(ViewerMotion.scrimAlpha(1f)).isEqualTo(0f) // grid fully revealed
    }

    @Test
    fun `retuned constants match the APP-711 delta table verbatim`() {
        // Guards against a silent drift back to the APP-692 numbers (the §7 delta table is the contract).
        assertThat(ViewerMotion.ThresholdFraction).isEqualTo(0.12f)
        assertThat(ViewerMotion.ThresholdDistanceMin.value).isEqualTo(80f)
        assertThat(ViewerMotion.ThresholdDistanceMax.value).isEqualTo(160f)
        assertThat(ViewerMotion.ThresholdVelocityDpPerSec).isEqualTo(1000f)
        assertThat(ViewerMotion.MinDismissScale).isEqualTo(0.60f)
        assertThat(ViewerMotion.CloseDurationMs).isEqualTo(240)
        assertThat(ViewerMotion.VerticalLockRatio).isEqualTo(1.0f)
        assertThat(ViewerMotion.DirectionLockSlop.value).isEqualTo(12f)
    }
}
