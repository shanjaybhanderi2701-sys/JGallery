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
