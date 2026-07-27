package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.TimelineSkeleton
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Unit-proves the section-skeleton derivation (APP-700). The builder is the O(#sections)-memory
 * replacement for `buildPhotosTimeline`'s nine parallel N-length arrays; these pin its section
 * boundaries, ordinals and counts so a diff against the display path (the golden-equivalence gate)
 * has a trustworthy foundation.
 */
class TimelineSkeletonBuilderTest {

    private fun day(y: Int, m: Int, d: Int): Long = LocalDate.of(y, m, d).toEpochDay()

    // --- time-sort path (GROUP BY localDayKey) ---------------------------------------------------

    @Test
    fun `fromDayCounts DAY makes one run per day with running ordinals`() {
        val rows = listOf(
            DayCountRow(dayKey = day(2024, 3, 15), itemCount = 3),
            DayCountRow(dayKey = day(2024, 3, 14), itemCount = 2),
            DayCountRow(dayKey = day(2024, 3, 10), itemCount = 1),
        )

        val skeleton = TimelineSkeletonBuilder.fromDayCounts(rows, GroupBy.DAY)

        assertThat(skeleton.totalItems).isEqualTo(6)
        assertThat(skeleton.sections.map { Triple(it.sectionKey, it.firstItemOrdinal, it.count) })
            .containsExactly(
                Triple(day(2024, 3, 15), 0, 3),
                Triple(day(2024, 3, 14), 3, 2),
                Triple(day(2024, 3, 10), 5, 1),
            )
            .inOrder()
    }

    @Test
    fun `fromDayCounts MONTH merges consecutive days of the same month`() {
        val rows = listOf(
            DayCountRow(day(2024, 3, 15), 2),
            DayCountRow(day(2024, 3, 2), 1),
            DayCountRow(day(2024, 2, 20), 4),
            DayCountRow(day(2024, 2, 1), 1),
        )

        val skeleton = TimelineSkeletonBuilder.fromDayCounts(rows, GroupBy.MONTH)

        // year*12 + (month-1)
        val march = 2024L * 12L + 2L
        val feb = 2024L * 12L + 1L
        assertThat(skeleton.totalItems).isEqualTo(8)
        assertThat(skeleton.sections.map { Triple(it.sectionKey, it.firstItemOrdinal, it.count) })
            .containsExactly(
                Triple(march, 0, 3),
                Triple(feb, 3, 5),
            )
            .inOrder()
    }

    @Test
    fun `fromDayCounts YEAR merges consecutive days of the same year`() {
        val rows = listOf(
            DayCountRow(day(2024, 3, 15), 2),
            DayCountRow(day(2024, 1, 2), 1),
            DayCountRow(day(2023, 12, 31), 3),
        )

        val skeleton = TimelineSkeletonBuilder.fromDayCounts(rows, GroupBy.YEAR)

        assertThat(skeleton.sections.map { Triple(it.sectionKey, it.firstItemOrdinal, it.count) })
            .containsExactly(
                Triple(2024L, 0, 3),
                Triple(2023L, 3, 3),
            )
            .inOrder()
    }

    @Test
    fun `fromDayCounts NONE is a single header-less run over everything`() {
        val rows = listOf(DayCountRow(day(2024, 3, 15), 2), DayCountRow(day(2024, 3, 14), 5))

        val skeleton = TimelineSkeletonBuilder.fromDayCounts(rows, GroupBy.NONE)

        assertThat(skeleton.totalItems).isEqualTo(7)
        assertThat(skeleton.sections).hasSize(1)
        assertThat(skeleton.sections.single().firstItemOrdinal).isEqualTo(0)
        assertThat(skeleton.sections.single().count).isEqualTo(7)
    }

    @Test
    fun `fromDayCounts empty is EMPTY`() {
        assertThat(TimelineSkeletonBuilder.fromDayCounts(emptyList(), GroupBy.DAY))
            .isEqualTo(TimelineSkeleton.EMPTY)
    }

    // --- name/size-sort path (RLE of the non-monotonic per-item projection, APP-704 D3) ----------

    @Test
    fun `fromDayKeys DAY run-length-encodes a non-monotonic projection into interleaved runs`() {
        // e.g. a name sort where the same day reappears out of date order.
        val d1 = day(2024, 3, 15)
        val d3 = day(2024, 3, 10)
        val projection = listOf(d1, d1, d3, d1) // A(d1) B(d1) C(d3) D(d1) in name order

        val skeleton = TimelineSkeletonBuilder.fromDayKeys(projection, GroupBy.DAY)

        assertThat(skeleton.totalItems).isEqualTo(4)
        assertThat(skeleton.sections.map { Triple(it.sectionKey, it.firstItemOrdinal, it.count) })
            .containsExactly(
                Triple(d1, 0, 2),
                Triple(d3, 2, 1),
                Triple(d1, 3, 1), // the same calendar day opens a SECOND section — cannot GROUP BY (F2)
            )
            .inOrder()
    }

    @Test
    fun `fromDayKeys MONTH cuts on month change along sort order`() {
        val projection = listOf(day(2024, 3, 15), day(2024, 2, 1), day(2024, 3, 2))
        val march = 2024L * 12L + 2L
        val feb = 2024L * 12L + 1L

        val skeleton = TimelineSkeletonBuilder.fromDayKeys(projection, GroupBy.MONTH)

        assertThat(skeleton.sections.map { Pair(it.sectionKey, it.count) })
            .containsExactly(Pair(march, 1), Pair(feb, 1), Pair(march, 1))
            .inOrder()
    }

    @Test
    fun `fromDayKeys NONE is a single run`() {
        val skeleton = TimelineSkeletonBuilder.fromDayKeys(listOf(day(2024, 1, 1), day(2024, 1, 2)), GroupBy.NONE)
        assertThat(skeleton.sections).hasSize(1)
        assertThat(skeleton.sections.single().count).isEqualTo(2)
    }

    @Test
    fun `fromDayKeys empty is EMPTY`() {
        assertThat(TimelineSkeletonBuilder.fromDayKeys(emptyList(), GroupBy.DAY))
            .isEqualTo(TimelineSkeleton.EMPTY)
    }
}
