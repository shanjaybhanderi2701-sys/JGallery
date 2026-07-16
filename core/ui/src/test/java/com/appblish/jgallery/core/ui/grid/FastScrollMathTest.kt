package com.appblish.jgallery.core.ui.grid

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

/** The fast-scroll mapping rules (design §3): linear index map, snap points, visibility, collapse. */
class FastScrollMathTest {

    @Test
    fun `thumb only exists past four viewports of content`() {
        assertThat(FastScrollMath.deepEnough(totalItems = 100, visibleItems = 20)).isTrue()
        assertThat(FastScrollMath.deepEnough(totalItems = 80, visibleItems = 20)).isFalse()
        assertThat(FastScrollMath.deepEnough(totalItems = 100, visibleItems = 0)).isFalse()
        assertThat(FastScrollMath.deepEnough(totalItems = 10_045, visibleItems = 24)).isTrue()
    }

    @Test
    fun `thumb fraction spans zero to one across the scrollable range`() {
        assertThat(FastScrollMath.thumbFraction(0, 20, 100)).isEqualTo(0f)
        assertThat(FastScrollMath.thumbFraction(80, 20, 100)).isEqualTo(1f)
        assertThat(FastScrollMath.thumbFraction(40, 20, 100)).isEqualTo(0.5f)
        // Overshoot clamps rather than escaping the track.
        assertThat(FastScrollMath.thumbFraction(999, 20, 100)).isEqualTo(1f)
    }

    @Test
    fun `drag fraction inverts thumb fraction over the scrollable range`() {
        // targetIndex maps over [0, total - visible] — the same range thumbFraction maps out of — so
        // scrolling to the result settles the thumb back at the released fraction (item 7).
        assertThat(FastScrollMath.targetIndex(0f, 10_000, 24)).isEqualTo(0)
        assertThat(FastScrollMath.targetIndex(1f, 10_000, 24)).isEqualTo(9_976) // last first-visible index
        assertThat(FastScrollMath.targetIndex(0.5f, 100, 20)).isEqualTo(40)     // 0.5 * (100 - 20)
        // Round-trip: thumbFraction(target) == released fraction, so the thumb does not jump on release.
        val target = FastScrollMath.targetIndex(0.5f, 100, 20)
        assertThat(FastScrollMath.thumbFraction(target, 20, 100)).isEqualTo(0.5f)
        // Degenerate inputs clamp instead of throwing.
        assertThat(FastScrollMath.targetIndex(0.5f, 0, 20)).isEqualTo(0)
        assertThat(FastScrollMath.targetIndex(-1f, 100, 20)).isEqualTo(0)
        assertThat(FastScrollMath.targetIndex(2f, 100, 20)).isEqualTo(80)
        // Empty layout (visible == 0) falls back to a full-range map rather than dividing by nothing.
        assertThat(FastScrollMath.targetIndex(1f, 100, 0)).isEqualTo(99)
    }

    @Test
    fun `finger maps to thumb centre over the travel range`() {
        // Re-derived from CalcVault (APP-496 item 3): the finger drives the thumb *centre* over the
        // travel range [0, track - thumb], the exact inverse of the render offset `fraction * travel`.
        val track = 1_000f
        val thumb = 100f
        // Finger at the thumb's half-height → fraction 0 (thumb parked at the very top).
        assertThat(FastScrollMath.thumbTravelFraction(50f, track, thumb)).isEqualTo(0f)
        // Finger at track-minus-half → fraction 1 (thumb parked at the very bottom).
        assertThat(FastScrollMath.thumbTravelFraction(950f, track, thumb)).isEqualTo(1f)
        // Finger at the track centre → 0.5.
        assertThat(FastScrollMath.thumbTravelFraction(500f, track, thumb)).isEqualTo(0.5f)
        // Overshoot past either end clamps into [0,1] rather than escaping the track.
        assertThat(FastScrollMath.thumbTravelFraction(-40f, track, thumb)).isEqualTo(0f)
        assertThat(FastScrollMath.thumbTravelFraction(5_000f, track, thumb)).isEqualTo(1f)
        // Degenerate geometry (thumb taller than track → nowhere to travel) collapses to 0.
        assertThat(FastScrollMath.thumbTravelFraction(30f, 40f, 100f)).isEqualTo(0f)
    }

    @Test
    fun `byte size formats in binary units with one decimal from KB up`() {
        assertThat(FastScrollMath.formatByteSize(0, Locale.US)).isEqualTo("0 B")
        assertThat(FastScrollMath.formatByteSize(-10, Locale.US)).isEqualTo("0 B")
        assertThat(FastScrollMath.formatByteSize(512, Locale.US)).isEqualTo("512 B")
        assertThat(FastScrollMath.formatByteSize(1_024, Locale.US)).isEqualTo("1.0 KB")
        assertThat(FastScrollMath.formatByteSize(1_536, Locale.US)).isEqualTo("1.5 KB")
        assertThat(FastScrollMath.formatByteSize(4_509_715, Locale.US)).isEqualTo("4.3 MB")
        assertThat(FastScrollMath.formatByteSize(3L * 1_024 * 1_024 * 1_024, Locale.US)).isEqualTo("3.0 GB")
    }

    @Test
    fun `release snaps to the nearest section start`() {
        val sections = listOf(0, 120, 480, 900)
        assertThat(FastScrollMath.nearestSectionStart(130, sections)).isEqualTo(120)
        assertThat(FastScrollMath.nearestSectionStart(400, sections)).isEqualTo(480)
        assertThat(FastScrollMath.nearestSectionStart(0, sections)).isEqualTo(0)
        assertThat(FastScrollMath.nearestSectionStart(5_000, sections)).isEqualTo(900)
        // No sections (albums-style grid): the landing index stands.
        assertThat(FastScrollMath.nearestSectionStart(42, emptyList())).isEqualTo(42)
    }

    @Test
    fun `bubble collapses to year only at speed`() {
        assertThat(FastScrollMath.bubbleCollapsed(0.001f)).isFalse()
        assertThat(FastScrollMath.bubbleCollapsed(0.01f)).isTrue()
        assertThat(FastScrollMath.bubbleCollapsed(-0.01f)).isTrue() // direction-agnostic
    }

    @Test
    fun `position label groups digits and clamps to range`() {
        assertThat(FastScrollMath.formatItemPosition(8_412, 61_908, Locale.US))
            .isEqualTo("item 8,412 of 61,908")
        // Overshoot during a layout race clamps to the ends instead of reading past them.
        assertThat(FastScrollMath.formatItemPosition(0, 5, Locale.US)).isEqualTo("item 1 of 5")
        assertThat(FastScrollMath.formatItemPosition(999, 5, Locale.US)).isEqualTo("item 5 of 5")
        // Empty grid: nothing to count, caller falls back to the month label alone.
        assertThat(FastScrollMath.formatItemPosition(1, 0, Locale.US)).isNull()
    }
}
