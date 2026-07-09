package com.appblish.jgallery.core.ui.grid

import com.google.common.truth.Truth.assertThat
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
    fun `drag fraction maps linearly onto the full index`() {
        assertThat(FastScrollMath.targetIndex(0f, 10_000)).isEqualTo(0)
        assertThat(FastScrollMath.targetIndex(1f, 10_000)).isEqualTo(9_999)
        assertThat(FastScrollMath.targetIndex(0.5f, 10_001)).isEqualTo(5_000)
        assertThat(FastScrollMath.targetIndex(0.5f, 0)).isEqualTo(0)
        assertThat(FastScrollMath.targetIndex(-1f, 100)).isEqualTo(0)
        assertThat(FastScrollMath.targetIndex(2f, 100)).isEqualTo(99)
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
}
