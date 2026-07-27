package com.appblish.jgallery.core.model

/** Sort keys offered in the "Sort By" sheet (spec §6). Operates on the cached index (fast). */
enum class SortKey { FILE_NAME, FILE_PATH, FILE_SIZE, LAST_MODIFIED }

enum class SortDirection { ASCENDING, DESCENDING }

data class SortSpec(
    val key: SortKey = SortKey.LAST_MODIFIED,
    val direction: SortDirection = SortDirection.DESCENDING,
)

/**
 * Grid column count (spec §4, §6). Clamped on construction.
 *
 * The user-adjustable **phone-portrait preference** spans [MIN]..[PREF_MAX]; on larger widths the
 * adaptive size-class bonus (APP-645 §5 / APP-653) may raise the *rendered* count up to [MAX], so the
 * value class itself accepts [MIN]..[MAX]. That bonus is applied at render only and never persisted, so
 * the saved preference never exceeds [PREF_MAX].
 */
@JvmInline
value class ColumnCount(val value: Int) {
    init { require(value in MIN..MAX) { "Column count must be in $MIN..$MAX, was $value" } }
    companion object {
        const val MIN = 2

        /**
         * Hard ceiling the value class accepts. Raised 6 → 10 for large-screen adaptive columns
         * (APP-645 §5, APP-653): the width-derived bonus can push the *rendered* count this high on
         * Expanded tablets/foldables. This is **not** the ceiling for the user preference — see [PREF_MAX].
         */
        const val MAX = 10

        /**
         * Ceiling for the persisted, pinch-adjustable phone-portrait preference. Kept at 6 so phone
         * tiles never fall below the 48dp touch target / heart-badge legibility (APP-645 §9). The
         * adaptive bonus is render-only and never written back, so the saved pref never exceeds this.
         * Pinch ([columnsForPinch]) and the Settings density sheet both clamp to it via [clamp].
         */
        const val PREF_MAX = 6

        val DEFAULT = ColumnCount(3)

        /** Clamp a user/persisted preference into the phone-portrait range [MIN]..[PREF_MAX]. */
        fun clamp(raw: Int) = ColumnCount(raw.coerceIn(MIN, PREF_MAX))
    }
}
