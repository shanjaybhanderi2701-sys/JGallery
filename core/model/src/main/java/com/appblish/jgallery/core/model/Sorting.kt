package com.appblish.jgallery.core.model

/** Sort keys offered in the "Sort By" sheet (spec §6). Operates on the cached index (fast). */
enum class SortKey { FILE_NAME, FILE_PATH, FILE_SIZE, LAST_MODIFIED }

enum class SortDirection { ASCENDING, DESCENDING }

data class SortSpec(
    val key: SortKey = SortKey.LAST_MODIFIED,
    val direction: SortDirection = SortDirection.DESCENDING,
)

/** Grid column count, 2–6 (spec §4, §6). Clamped on construction. */
@JvmInline
value class ColumnCount(val value: Int) {
    init { require(value in MIN..MAX) { "Column count must be in $MIN..$MAX, was $value" } }
    companion object {
        const val MIN = 2
        const val MAX = 6
        val DEFAULT = ColumnCount(3)
        fun clamp(raw: Int) = ColumnCount(raw.coerceIn(MIN, MAX))
    }
}
