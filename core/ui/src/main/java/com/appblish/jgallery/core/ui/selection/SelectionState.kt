package com.appblish.jgallery.core.ui.selection

/**
 * Immutable multi-select state over item keys of type [T] (spec §7.6). Pure Kotlin, so it is
 * JVM-unit-testable and grid-agnostic: the Photos time-grid and the album-detail grid both drive the
 * exact same state holder — the only thing that varies is which keys they feed it.
 *
 * The grid owns the *ordering* of keys (its current visible list); this type only tracks membership
 * and the range **anchor**. Range math lives in [rangeSpan] rather than here so it can be unit-tested
 * against an explicit ordering without constructing a grid.
 */
data class SelectionState<T>(
    val selected: Set<T> = emptySet(),
    /** The last item a tap/long-press/drag-start touched — the fixed end of a drag range-select. */
    val anchor: T? = null,
) {
    /** Selection mode is on iff at least one item is selected (long-press enters, last tap-off exits). */
    val isActive: Boolean get() = selected.isNotEmpty()
    val count: Int get() = selected.size

    fun isSelected(key: T): Boolean = key in selected

    /** Tap toggles a single item and moves the anchor to it (spec §7.6 "tap toggles"). */
    fun toggle(key: T): SelectionState<T> = copy(
        selected = if (key in selected) selected - key else selected + key,
        anchor = key,
    )

    /** Long-press / drag-start on [key]: ensure it is selected and make it the anchor (idempotent). */
    fun anchorOn(key: T): SelectionState<T> = copy(selected = selected + key, anchor = key)

    /**
     * Drag range-select (spec §7.6 "drag across adjacent items"): union [base] with the inclusive
     * span from the current [anchor] to [key] within [ordered]. Passing the pre-drag [base] (not the
     * live selection) is what makes shrinking the drag *deselect* items the drag no longer covers,
     * while preserving everything selected before the drag began. Falls back to [anchorOn] with no
     * anchor or unknown keys.
     */
    fun extendRangeTo(key: T, ordered: List<T>, base: Set<T>): SelectionState<T> {
        val a = anchor ?: return anchorOn(key)
        val span = rangeSpan(a, key, ordered)
        if (span.isEmpty()) return anchorOn(key)
        return copy(selected = base + span)
    }

    /** Select All (spec §7.6): add every currently-known key; keeps the anchor. */
    fun selectAll(all: Collection<T>): SelectionState<T> = copy(selected = selected + all)

    /** Exit selection mode entirely. */
    fun clear(): SelectionState<T> = SelectionState()
}

/**
 * The inclusive key span between the items at [anchor] and [current] positions in [ordered], in
 * list order. Empty when either key is absent (e.g. an item scrolled out of the known list) so
 * callers can degrade gracefully instead of selecting a bogus range.
 */
fun <T> rangeSpan(anchor: T, current: T, ordered: List<T>): List<T> {
    val i = ordered.indexOf(anchor)
    val j = ordered.indexOf(current)
    if (i < 0 || j < 0) return emptyList()
    return ordered.subList(minOf(i, j), maxOf(i, j) + 1)
}
