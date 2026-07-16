package com.appblish.jgallery.feature.search

import com.appblish.jgallery.core.model.DateRange

/**
 * The Date filter row of the Search screen (design 502-D §3/§4, spec §4.3). A small single-select
 * sibling to [com.appblish.jgallery.core.model.MediaFilter] that drives the second chip row; each
 * facet resolves to a device-local [DateRange] at query time via the 502-A domain factories, so
 * day/week/month/year boundaries follow the device clock.
 *
 * [token] is the stable, human-readable string persisted on a [RecentSearch.dateFacet] — kept opaque
 * so a re-run reconstructs the same facet ([fromToken]) and it stays forward-compatible with the G3
 * date model. Default selection is **none** (null): tapping the selected chip clears it back to no
 * range (design §4).
 */
enum class DateFacet(val token: String) {
    TODAY("Today"),
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    THIS_YEAR("This year");

    /** The concrete range this facet covers, computed in the device-local zone at [nowMillis]. */
    fun range(nowMillis: Long = System.currentTimeMillis()): DateRange = when (this) {
        TODAY -> DateRange.today(nowMillis)
        THIS_WEEK -> DateRange.thisWeek(nowMillis)
        THIS_MONTH -> DateRange.thisMonth(nowMillis)
        THIS_YEAR -> DateRange.thisYear(nowMillis)
    }

    companion object {
        /** Chip order rendered left-to-right (design §3.1). */
        val ORDER: List<DateFacet> = entries.toList()

        /** The facet whose [token] equals [token], or null (no/unknown facet ⇒ no date range). */
        fun fromToken(token: String?): DateFacet? = entries.firstOrNull { it.token == token }
    }
}
