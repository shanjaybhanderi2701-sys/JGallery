package com.appblish.jgallery.core.ui.selection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The pure selection model (spec §7.6). These cover the tricky bits — drag range union against a
 * pre-drag base (so shrinking a drag *deselects*), order-independent spans, and graceful handling of
 * keys that scrolled out of the known list — without a Compose runtime.
 */
class SelectionStateTest {

    private val ids = ('a'..'h').map { it.toString() } // ordered grid keys

    @Test fun `tap toggles membership and moves the anchor`() {
        val s = SelectionState<String>().toggle("c")
        assertThat(s.isSelected("c")).isTrue()
        assertThat(s.anchor).isEqualTo("c")
        assertThat(s.isActive).isTrue()

        val off = s.toggle("c")
        assertThat(off.isSelected("c")).isFalse()
        assertThat(off.isActive).isFalse()
    }

    @Test fun `anchorOn is idempotent add and sets anchor`() {
        val s = SelectionState<String>().anchorOn("b").anchorOn("b")
        assertThat(s.selected).containsExactly("b")
        assertThat(s.anchor).isEqualTo("b")
    }

    @Test fun `drag range selects the inclusive span from anchor, either direction`() {
        val start = SelectionState<String>().anchorOn("b") // anchor = b
        val forward = start.extendRangeTo("e", ids, base = start.selected)
        assertThat(forward.selected).containsExactly("b", "c", "d", "e")

        // Dragging the other way from the same anchor.
        val backward = SelectionState<String>().anchorOn("e").let { it.extendRangeTo("b", ids, it.selected) }
        assertThat(backward.selected).containsExactly("b", "c", "d", "e")
    }

    @Test fun `shrinking a drag deselects items the drag no longer covers`() {
        // Pre-drag selection has an unrelated item 'h' that must be preserved.
        val base = setOf("h")
        val anchored = SelectionState(selected = base + "b", anchor = "b")
        val wide = anchored.extendRangeTo("f", ids, base = base) // b..f + h
        assertThat(wide.selected).containsExactly("b", "c", "d", "e", "f", "h")

        val shrunk = wide.extendRangeTo("d", ids, base = base) // back to b..d + h
        assertThat(shrunk.selected).containsExactly("b", "c", "d", "h")
    }

    @Test fun `extendRange with no anchor falls back to a single select`() {
        val s = SelectionState<String>().extendRangeTo("c", ids, base = emptySet())
        assertThat(s.selected).containsExactly("c")
        assertThat(s.anchor).isEqualTo("c")
    }

    @Test fun `selectAll unions and clear resets`() {
        val all = SelectionState<String>().toggle("a").selectAll(ids)
        assertThat(all.selected).containsExactlyElementsIn(ids)
        assertThat(all.clear().isActive).isFalse()
    }

    @Test fun `rangeSpan is order-independent and empty for absent keys`() {
        assertThat(rangeSpan("d", "b", ids)).containsExactly("b", "c", "d").inOrder()
        assertThat(rangeSpan("b", "d", ids)).containsExactly("b", "c", "d").inOrder()
        assertThat(rangeSpan("b", "zzz", ids)).isEmpty()
        assertThat(rangeSpan("a", "a", ids)).containsExactly("a")
    }
}
