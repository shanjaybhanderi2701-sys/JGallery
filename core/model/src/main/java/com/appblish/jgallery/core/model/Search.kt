package com.appblish.jgallery.core.model

import java.text.Normalizer
import java.util.Calendar
import java.util.TimeZone

/**
 * A half-open instant range `[startMillis, endMillisExclusive)` in epoch millis (spec APP-502
 * §4.1/§4.2). Used to express a "date intent" facet of a [SearchQuery] against `dateTakenMillis`.
 *
 * The quick-chip factories ([today], [thisWeek], [thisMonth], [thisYear]) compute the range in the
 * **device-local** time zone at query time, so day/month/year boundaries land on local midnight and
 * roll over correctly (Dec→Jan, week-into-previous-month, leap days) — [java.util.Calendar] does the
 * calendar arithmetic. Kept off `java.time` on purpose: this module is consumed by the Android app,
 * which does not enable core-library desugaring, and `Calendar` is available on every API level.
 *
 * The range is half-open so adjacent ranges tile without overlap: "today" ends exactly where
 * "tomorrow" begins, and an item at local midnight belongs to the day it starts.
 */
data class DateRange(val startMillis: Long, val endMillisExclusive: Long) {

    /** True when [epochMillis] falls in `[startMillis, endMillisExclusive)`. */
    operator fun contains(epochMillis: Long): Boolean =
        epochMillis >= startMillis && epochMillis < endMillisExclusive

    companion object {
        /** Local calendar day containing [nowMillis]: `[00:00 today, 00:00 tomorrow)`. */
        fun today(nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): DateRange {
            val start = startOfDay(nowMillis, zone)
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            return DateRange(start.timeInMillis, end.timeInMillis)
        }

        /**
         * The Monday-started week containing [nowMillis]. Week start is fixed to Monday (ISO) rather
         * than the locale default so the boundary is deterministic and unit-testable.
         */
        fun thisWeek(nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): DateRange {
            val start = startOfDay(nowMillis, zone)
            val daysFromMonday = (start.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            start.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 7) }
            return DateRange(start.timeInMillis, end.timeInMillis)
        }

        /** The local calendar month containing [nowMillis]; end rolls into the next month/year. */
        fun thisMonth(nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): DateRange {
            val start = startOfDay(nowMillis, zone).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            return DateRange(start.timeInMillis, end.timeInMillis)
        }

        /** The local calendar year containing [nowMillis]; end rolls into Jan 1 of the next year. */
        fun thisYear(nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): DateRange {
            val start = startOfDay(nowMillis, zone).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.MONTH, Calendar.JANUARY)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
            return DateRange(start.timeInMillis, end.timeInMillis)
        }

        private fun startOfDay(nowMillis: Long, zone: TimeZone): Calendar =
            Calendar.getInstance(zone).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
    }
}

/**
 * A platform-free search over the in-memory index (spec APP-502 §4.1). Three optional facets are
 * combined with **AND** semantics — an item matches only when every *present* facet matches:
 * - [text]: a normalized (trim / lowercase / diacritic-stripped) substring of `displayName`. Blank
 *   text is not a facet and passes everything.
 * - [mediaType]: reuses [MediaFilter.matches]; [MediaFilter.ALL] passes everything.
 * - [dateRange]: `dateTakenMillis` must fall in the range; `null` passes everything.
 *
 * An [isEmpty] query (blank text, [MediaFilter.ALL], no range) is meaningless as a filter — it would
 * admit the whole library — so [matching] returns no results for it and the caller shows the
 * recent-searches / empty state instead (spec §4.1, §5 AC4). Lives in `:core:model` next to
 * [MediaFilter] and is JVM-unit-tested (spec §6).
 */
data class SearchQuery(
    val text: String = "",
    val mediaType: MediaFilter = MediaFilter.ALL,
    val dateRange: DateRange? = null,
) {
    /** True when no facet constrains anything, so the query cannot meaningfully narrow the library. */
    val isEmpty: Boolean
        get() = text.isBlank() && mediaType == MediaFilter.ALL && dateRange == null

    /**
     * True when [item] satisfies every present facet (AND). Note this is a pure facet test: an
     * [isEmpty] query returns `true` for every item. The "empty query shows nothing" rule is a
     * result-list concern handled by [matching]; call that, not this, to filter a library.
     */
    fun matches(item: MediaItem): Boolean {
        if (!mediaType.matches(item)) return false
        if (dateRange != null && item.dateTakenMillis !in dateRange) return false
        val needle = text.normalizeForSearch()
        if (needle.isNotEmpty() && !item.displayName.normalizeForSearch().contains(needle)) return false
        return true
    }
}

/**
 * Apply [query] to this list (spec §4.1). An [SearchQuery.isEmpty] query yields **no results** (the
 * caller shows the recent/empty state, AC4); otherwise returns the items matching every facet, in
 * the original order.
 */
fun List<MediaItem>.matching(query: SearchQuery): List<MediaItem> =
    if (query.isEmpty) emptyList() else filter { query.matches(it) }

/**
 * Fold a string to its search form: trimmed, locale-independently lower-cased, and stripped of
 * combining diacritics (`café` → `cafe`) via NFD decomposition. Used on both the query text and the
 * `displayName` so matching is case- and accent-insensitive (spec §4.1).
 */
fun String.normalizeForSearch(): String {
    val folded = trim().lowercase()
    val decomposed = Normalizer.normalize(folded, Normalizer.Form.NFD)
    return NONSPACING_MARKS.replace(decomposed, "")
}

private val NONSPACING_MARKS = Regex("\\p{Mn}+")
