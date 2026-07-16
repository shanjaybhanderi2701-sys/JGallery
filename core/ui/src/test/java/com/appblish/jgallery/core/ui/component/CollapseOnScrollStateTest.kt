package com.appblish.jgallery.core.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Direction logic for the collapse-on-scroll chip bar (design G1-D8 item 4): scroll-up (negative dy)
 * hides, scroll-down (positive dy) reveals, and a sub-threshold jitter changes nothing.
 */
class CollapseOnScrollStateTest {

    private val drag = NestedScrollSource.UserInput

    private fun CollapseOnScrollState.preScroll(dy: Float) =
        connection.onPreScroll(Offset(0f, dy), drag)

    @Test
    fun `scrolling content up hides the bar`() {
        val state = CollapseOnScrollState(initiallyVisible = true)
        val consumed = state.preScroll(-40f)
        assertThat(state.visible).isFalse()
        // Reads direction only — never consumes, so the grid still scrolls.
        assertThat(consumed).isEqualTo(Offset.Zero)
    }

    @Test
    fun `scrolling content down reveals the bar`() {
        val state = CollapseOnScrollState(initiallyVisible = false)
        state.preScroll(40f)
        assertThat(state.visible).isTrue()
    }

    @Test
    fun `direction changes drive the bar both ways`() {
        val state = CollapseOnScrollState(initiallyVisible = true)
        state.preScroll(-30f)
        assertThat(state.visible).isFalse()
        state.preScroll(30f)
        assertThat(state.visible).isTrue()
        state.preScroll(-30f)
        assertThat(state.visible).isFalse()
    }

    @Test
    fun `sub-threshold jitter leaves the bar untouched`() {
        val state = CollapseOnScrollState(initiallyVisible = true)
        state.preScroll(-0.5f)
        assertThat(state.visible).isTrue()
        state.preScroll(0.5f)
        assertThat(state.visible).isTrue()
    }

    @Test
    fun `reveal forces the bar back on`() {
        val state = CollapseOnScrollState(initiallyVisible = true)
        state.preScroll(-50f)
        assertThat(state.visible).isFalse()
        state.reveal()
        assertThat(state.visible).isTrue()
    }
}
