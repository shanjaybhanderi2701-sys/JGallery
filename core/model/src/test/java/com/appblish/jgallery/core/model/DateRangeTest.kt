package com.appblish.jgallery.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Test

/**
 * Spec §4.2 / §5 acceptance item 3: each quick date chip produces the correct **local-time**
 * `[start, end)` boundary, including week / month / year rollovers and DST-affected days.
 *
 * The production factories do their calendar math with [java.util.Calendar]; these tests use
 * `java.time` (available on the host JVM that runs unit tests) as an *independent oracle* so a bug
 * in the Calendar arithmetic can't hide behind an equally-wrong expectation.
 */
class DateRangeTest {

    /** Epoch millis of local midnight starting [date] in [zoneId] — the oracle. */
    private fun midnight(date: LocalDate, zoneId: ZoneId): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    /** Some instant during [date] in [zoneId] (noon), used as "now". */
    private fun noonOf(date: LocalDate, zoneId: ZoneId): Long =
        date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()

    private val ny = ZoneId.of("America/New_York")
    private val nyTz = TimeZone.getTimeZone("America/New_York")
    private val utc = ZoneId.of("UTC")
    private val utcTz = TimeZone.getTimeZone("UTC")

    @Test
    fun `today spans local midnight to next midnight`() {
        val date = LocalDate.of(2024, 6, 15)
        val range = DateRange.today(noonOf(date, ny), nyTz)
        assertThat(range.startMillis).isEqualTo(midnight(date, ny))
        assertThat(range.endMillisExclusive).isEqualTo(midnight(date.plusDays(1), ny))
    }

    @Test
    fun `today on a DST spring-forward day is only 23 hours`() {
        // 2024-03-10: America/New_York springs forward, so the local day is 23h long.
        val date = LocalDate.of(2024, 3, 10)
        val range = DateRange.today(noonOf(date, ny), nyTz)
        assertThat(range.startMillis).isEqualTo(midnight(date, ny))
        assertThat(range.endMillisExclusive).isEqualTo(midnight(date.plusDays(1), ny))
        assertThat(range.endMillisExclusive - range.startMillis).isEqualTo(23L * 60 * 60 * 1000)
    }

    @Test
    fun `thisWeek is Monday to Monday and can cross a month boundary`() {
        // Wed 2024-05-01 → week starts Mon 2024-04-29, ends Mon 2024-05-06.
        val now = noonOf(LocalDate.of(2024, 5, 1), utc)
        val range = DateRange.thisWeek(now, utcTz)
        assertThat(range.startMillis).isEqualTo(midnight(LocalDate.of(2024, 4, 29), utc))
        assertThat(range.endMillisExclusive).isEqualTo(midnight(LocalDate.of(2024, 5, 6), utc))
    }

    @Test
    fun `thisWeek when now is Sunday still uses the preceding Monday`() {
        // Sun 2024-06-16 → Monday of that ISO week is 2024-06-10.
        val now = noonOf(LocalDate.of(2024, 6, 16), utc)
        val range = DateRange.thisWeek(now, utcTz)
        assertThat(range.startMillis).isEqualTo(midnight(LocalDate.of(2024, 6, 10), utc))
        assertThat(range.endMillisExclusive).isEqualTo(midnight(LocalDate.of(2024, 6, 17), utc))
    }

    @Test
    fun `thisMonth rolls into the next year in December`() {
        val now = noonOf(LocalDate.of(2024, 12, 20), ny)
        val range = DateRange.thisMonth(now, nyTz)
        assertThat(range.startMillis).isEqualTo(midnight(LocalDate.of(2024, 12, 1), ny))
        assertThat(range.endMillisExclusive).isEqualTo(midnight(LocalDate.of(2025, 1, 1), ny))
    }

    @Test
    fun `thisMonth handles February in a leap year`() {
        val now = noonOf(LocalDate.of(2024, 2, 29), utc)
        val range = DateRange.thisMonth(now, utcTz)
        assertThat(range.startMillis).isEqualTo(midnight(LocalDate.of(2024, 2, 1), utc))
        assertThat(range.endMillisExclusive).isEqualTo(midnight(LocalDate.of(2024, 3, 1), utc))
    }

    @Test
    fun `thisYear spans Jan 1 to next Jan 1`() {
        val now = noonOf(LocalDate.of(2024, 7, 4), ny)
        val range = DateRange.thisYear(now, nyTz)
        assertThat(range.startMillis).isEqualTo(midnight(LocalDate.of(2024, 1, 1), ny))
        assertThat(range.endMillisExclusive).isEqualTo(midnight(LocalDate.of(2025, 1, 1), ny))
    }

    @Test
    fun `zone matters - the same instant lands on different local days`() {
        // 2024-06-15T02:00Z is still 2024-06-14 22:00 in New York.
        val instant = LocalDate.of(2024, 6, 15).atTime(2, 0).atZone(utc).toInstant().toEpochMilli()
        assertThat(DateRange.today(instant, utcTz).startMillis)
            .isEqualTo(midnight(LocalDate.of(2024, 6, 15), utc))
        assertThat(DateRange.today(instant, nyTz).startMillis)
            .isEqualTo(midnight(LocalDate.of(2024, 6, 14), ny))
    }

    @Test
    fun `contains is half-open`() {
        val range = DateRange(100L, 200L)
        assertThat(100L in range).isTrue()
        assertThat(150L in range).isTrue()
        assertThat(200L in range).isFalse()
        assertThat(99L in range).isFalse()
    }
}
