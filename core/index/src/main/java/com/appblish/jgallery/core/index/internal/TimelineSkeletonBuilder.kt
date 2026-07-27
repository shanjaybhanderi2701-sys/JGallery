package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.SectionRun
import com.appblish.jgallery.core.model.TimelineSkeleton
import java.time.LocalDate

/**
 * Derives the light [TimelineSkeleton] from the SQL layer's primitive output (APP-700, Option B).
 * Pure and platform-free apart from `java.time` arithmetic, so the section boundaries the UI draws are
 * unit-tested against `buildPhotosTimeline` on the JVM (the golden-equivalence acceptance gate,
 * APP-704 §4.6) without a Room/Android runtime.
 *
 * Two inputs, one output:
 * - [fromDayCounts] — the time-sort path: SQL already `GROUP BY localDayKey`-ed and ordered the days,
 *   so this is an O(#days) fold (DAY = 1 run/day; MONTH/YEAR = merge consecutive days).
 * - [fromDayKeys] — the name/size-sort path: the date key is not monotonic along the sort (APP-704
 *   F2/D3), so this run-length-encodes the ordered per-item `localDayKey` projection on the
 *   group-by-space section key. Primitives only — no `MediaItem`, no N-length String arrays.
 */
internal object TimelineSkeletonBuilder {

    fun fromDayCounts(rows: List<DayCountRow>, groupBy: GroupBy): TimelineSkeleton {
        if (rows.isEmpty()) return TimelineSkeleton.EMPTY
        val total = rows.sumOf { it.itemCount }
        val sections = ArrayList<SectionRun>()

        when (groupBy) {
            GroupBy.NONE ->
                sections += SectionRun(
                    sectionKey = 0L,
                    dayKey = rows.first().dayKey,
                    firstItemOrdinal = 0,
                    count = total,
                )

            GroupBy.DAY -> {
                var ordinal = 0
                for (row in rows) {
                    sections += SectionRun(
                        sectionKey = row.dayKey,
                        dayKey = row.dayKey,
                        firstItemOrdinal = ordinal,
                        count = row.itemCount,
                    )
                    ordinal += row.itemCount
                }
            }

            GroupBy.MONTH, GroupBy.YEAR -> {
                // Days arrive ordered, so members of a month/year are contiguous → merge each run.
                var ordinal = 0
                var i = 0
                while (i < rows.size) {
                    val startDay = rows[i].dayKey
                    val key = sectionKeyFor(startDay, groupBy)
                    val runOrdinal = ordinal
                    var count = 0
                    while (i < rows.size && sectionKeyFor(rows[i].dayKey, groupBy) == key) {
                        count += rows[i].itemCount
                        i++
                    }
                    sections += SectionRun(key, startDay, runOrdinal, count)
                    ordinal += count
                }
            }
        }
        return TimelineSkeleton(totalItems = total, sections = sections)
    }

    fun fromDayKeys(dayKeys: List<Long>, groupBy: GroupBy): TimelineSkeleton {
        val total = dayKeys.size
        if (total == 0) return TimelineSkeleton.EMPTY
        if (groupBy == GroupBy.NONE) {
            return TimelineSkeleton(total, listOf(SectionRun(0L, dayKeys.first(), 0, total)))
        }
        val sections = ArrayList<SectionRun>()
        var i = 0
        while (i < total) {
            val startDay = dayKeys[i]
            val key = sectionKeyFor(startDay, groupBy)
            val runOrdinal = i
            var count = 0
            while (i < total && sectionKeyFor(dayKeys[i], groupBy) == key) {
                count++
                i++
            }
            sections += SectionRun(key, startDay, runOrdinal, count)
        }
        return TimelineSkeleton(total, sections)
    }

    /**
     * The section-boundary key for a `localDayKey` under [groupBy]. **Must** stay byte-for-byte in
     * lockstep with `PhotoTimeline.sectionKeyFor` — the golden-equivalence test pins that.
     */
    private fun sectionKeyFor(dayKey: Long, groupBy: GroupBy): Long = when (groupBy) {
        GroupBy.DAY -> dayKey
        GroupBy.MONTH -> {
            val date = LocalDate.ofEpochDay(dayKey)
            date.year.toLong() * 12L + (date.monthValue - 1)
        }
        GroupBy.YEAR -> LocalDate.ofEpochDay(dayKey).year.toLong()
        GroupBy.NONE -> 0L
    }
}
