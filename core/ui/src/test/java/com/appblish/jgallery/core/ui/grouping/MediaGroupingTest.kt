package com.appblish.jgallery.core.ui.grouping

import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The shared album/photos grouping (APP-499): sectioning [buildMediaSections] applies to any media
 * grid so the Group-by menu behaves identically inside albums and on the home tab.
 */
class MediaGroupingTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 16)
    private val locale = Locale.US

    private fun at(date: LocalDate, id: String): MediaItem {
        val millis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return MediaItem(
            id = MediaId(id),
            displayName = "$id.jpg",
            type = MediaType.IMAGE,
            bucketId = "camera",
            bucketName = "Camera",
            dateTakenMillis = millis,
            dateModifiedMillis = millis,
            sizeBytes = 1,
            width = 10,
            height = 10,
            durationMillis = 0,
            mimeType = "image/jpeg",
        )
    }

    @Test
    fun `NONE yields a flat header-less run`() {
        val items = listOf(at(today, "a"), at(today.minusMonths(2), "b"))
        val sections = buildMediaSections(items, GroupBy.NONE, zone, today, locale)

        assertThat(sections.sectionStarts).isEmpty()
        assertThat(sections.cells).hasSize(2)
        assertThat(sections.cells.all { it is MediaCell.Tile }).isTrue()
    }

    @Test
    fun `DAY opens Today and Yesterday headers`() {
        val items = listOf(
            at(today, "a"),
            at(today, "b"),
            at(today.minusDays(1), "c"),
        )
        val sections = buildMediaSections(items, GroupBy.DAY, zone, today, locale)

        val headers = sections.cells.filterIsInstance<MediaCell.Header>().map { it.label }
        assertThat(headers).containsExactly("Today", "Yesterday").inOrder()
        // Two headers + three tiles interleaved.
        assertThat(sections.cells).hasSize(5)
        assertThat(sections.sectionStarts).containsExactly(0, 3).inOrder()
    }

    @Test
    fun `MONTH collapses same-month items under one header`() {
        val items = listOf(
            at(LocalDate.of(2026, 7, 2), "a"),
            at(LocalDate.of(2026, 7, 1), "b"),
            at(LocalDate.of(2026, 6, 30), "c"),
        )
        val sections = buildMediaSections(items, GroupBy.MONTH, zone, today, locale)

        val headers = sections.cells.filterIsInstance<MediaCell.Header>().map { it.label }
        assertThat(headers).containsExactly("July 2026", "June 2026").inOrder()
    }

    @Test
    fun `idAt returns null for headers and the media id for tiles`() {
        val items = listOf(at(today, "a"))
        val sections = buildMediaSections(items, GroupBy.DAY, zone, today, locale)

        assertThat(sections.idAt(0)).isNull() // "Today" header
        assertThat(sections.idAt(1)).isEqualTo("a") // tile
    }

    @Test
    fun `same-day items split across the sort order still yield one section per day (APP-609)`() {
        // An exported album shares one modified time across items whose capture days differ, so the
        // Sort-by-modified order interleaves days: [dayA, dayB, dayA, dayB]. Cutting sections naively
        // over that order would re-open dayA/dayB and reuse their "media_header:<epochDay>" LazyGrid
        // keys — a hard duplicate-key crash (the AlbumDetailScreen fatal reported on APP-600). Bucketing
        // keeps each day to a single, contiguous section.
        val dayA = today
        val dayB = today.minusDays(1)
        val items = listOf(at(dayA, "a1"), at(dayB, "b1"), at(dayA, "a2"), at(dayB, "b2"))

        val sections = buildMediaSections(items, GroupBy.DAY, zone, today, locale)

        val headerKeys = sections.cells.filterIsInstance<MediaCell.Header>().map { it.key }
        assertThat(headerKeys).containsNoDuplicates()
        assertThat(headerKeys).hasSize(2) // one per day, not one per run
        // First-encounter section order (Today before Yesterday) with each day's tiles contiguous and
        // in their original intra-section order.
        val layout = sections.cells.map { cell ->
            when (cell) {
                is MediaCell.Header -> "H:${cell.label}"
                is MediaCell.Tile -> cell.item.id.value
            }
        }
        assertThat(layout).containsExactly("H:Today", "a1", "a2", "H:Yesterday", "b1", "b2").inOrder()
    }

    @Test
    fun `YEAR groups across months of the same year`() {
        val items = listOf(
            at(LocalDate.of(2026, 3, 1), "a"),
            at(LocalDate.of(2026, 1, 1), "b"),
            at(LocalDate.of(2025, 12, 1), "c"),
        )
        val sections = buildMediaSections(items, GroupBy.YEAR, zone, today, locale)

        val headers = sections.cells.filterIsInstance<MediaCell.Header>().map { it.label }
        assertThat(headers).containsExactly("2026", "2025").inOrder()
    }
}
