package com.appblish.jgallery.feature.photos

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.Locale

/**
 * The date-grouping contract behind the Photos stream (spec §4, design a05/a07/a14): section labels,
 * newest-first ordering, capture-time fallback, fast-scroll snap points, and bubble labels.
 */
class PhotoTimelineTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 7, 9)
    private val locale = Locale.UK

    private fun item(
        id: String,
        takenAt: LocalDate?,
        modifiedAt: LocalDate = LocalDate.of(2020, 1, 1),
        type: MediaType = MediaType.IMAGE,
        durationMillis: Long = 0,
    ) = MediaItem(
        id = MediaId(id),
        displayName = "$id.jpg",
        type = type,
        bucketId = "b1",
        bucketName = "Camera",
        dateTakenMillis = takenAt?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L,
        dateModifiedMillis = modifiedAt.atStartOfDay(zone).toInstant().toEpochMilli(),
        sizeBytes = 1,
        width = 100,
        height = 100,
        durationMillis = durationMillis,
        mimeType = if (type == MediaType.IMAGE) "image/jpeg" else "video/mp4",
    )

    @Test
    fun `groups newest-first with Today, Yesterday, then dated headers`() {
        val timeline = buildPhotosTimeline(
            items = listOf(
                item("old", takenAt = LocalDate.of(2026, 6, 20)),
                item("today_1", takenAt = today),
                item("yesterday_1", takenAt = today.minusDays(1)),
                item("today_2", takenAt = today),
            ),
            zone = zone,
            today = today,
            locale = locale,
        )

        val headers = timeline.cells.filterIsInstance<PhotosCell.DateHeader>().map { it.label }
        assertThat(headers).containsExactly("Today", "Yesterday", "20/06/2026").inOrder()

        // Today's section holds both of today's items, directly after its header.
        assertThat(timeline.cells[0]).isInstanceOf(PhotosCell.DateHeader::class.java)
        val todayTiles = timeline.cells.subList(1, 3).filterIsInstance<PhotosCell.Tile>()
        assertThat(todayTiles.map { it.item.id.value }).containsExactly("today_1", "today_2")

        assertThat(timeline.itemCount).isEqualTo(4)
    }

    @Test
    fun `falls back to modified time when capture time is missing`() {
        val timeline = buildPhotosTimeline(
            items = listOf(
                item("no_metadata", takenAt = null, modifiedAt = today), // dateTaken == 0
                item("dated", takenAt = LocalDate.of(2026, 1, 5)),
            ),
            zone = zone,
            today = today,
            locale = locale,
        )

        // The metadata-less item sorts by its modified time (today) — first section.
        val first = timeline.cells.filterIsInstance<PhotosCell.Tile>().first()
        assertThat(first.item.id.value).isEqualTo("no_metadata")
        val headers = timeline.cells.filterIsInstance<PhotosCell.DateHeader>().map { it.label }
        assertThat(headers).containsExactly("Today", "05/01/2026").inOrder()
    }

    @Test
    fun `sectionStarts index every header and nothing else`() {
        val timeline = buildPhotosTimeline(
            items = listOf(
                item("a", takenAt = today),
                item("b", takenAt = today.minusDays(3)),
                item("c", takenAt = today.minusDays(3)),
                item("d", takenAt = today.minusDays(40)),
            ),
            zone = zone,
            today = today,
            locale = locale,
        )

        assertThat(timeline.sectionStarts).hasSize(3)
        timeline.sectionStarts.forEach { start ->
            assertThat(timeline.cells[start]).isInstanceOf(PhotosCell.DateHeader::class.java)
        }
    }

    @Test
    fun `bubble labels give month-year normally and year when collapsed`() {
        val timeline = buildPhotosTimeline(
            items = listOf(item("a", takenAt = LocalDate.of(2026, 7, 9))),
            zone = zone,
            today = today,
            locale = locale,
        )

        assertThat(timeline.bubbleLabel(1, collapsed = false)).isEqualTo("July 2026")
        assertThat(timeline.bubbleLabel(1, collapsed = true)).isEqualTo("2026")
        // Out-of-range indices clamp instead of crashing (drag can overshoot during layout races).
        assertThat(timeline.bubbleLabel(99, collapsed = false)).isEqualTo("July 2026")
        assertThat(timeline.bubbleLabel(-5, collapsed = true)).isEqualTo("2026")
    }

    @Test
    fun `keys are stable and unique across headers and tiles`() {
        val timeline = buildPhotosTimeline(
            items = (1..50).map { item("id_$it", takenAt = today.minusDays((it % 5).toLong())) },
            zone = zone,
            today = today,
            locale = locale,
        )

        val keys = timeline.cells.map { it.key }
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `equal timestamps break ties deterministically by id`() {
        val ts = today.minusDays(2)
        val a = buildPhotosTimeline(listOf(item("x", ts), item("y", ts)), zone, today, locale)
        val b = buildPhotosTimeline(listOf(item("y", ts), item("x", ts)), zone, today, locale)
        assertThat(a.cells.map { it.key }).isEqualTo(b.cells.map { it.key })
    }

    @Test
    fun `duration badge formatting covers minute and hour scales`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
        assertThat(formatDuration(65_000)).isEqualTo("1:05")
        assertThat(formatDuration(727_000)).isEqualTo("12:07")
        assertThat(formatDuration(5_025_000)).isEqualTo("1:23:45")
        assertThat(formatDuration(-10)).isEqualTo("0:00")
    }

    @Test
    fun `ten thousand items build one cell per item plus headers`() {
        val items = (1..10_000).map { item("id_$it", takenAt = today.minusDays((it / 100).toLong())) }
        val timeline = buildPhotosTimeline(items, zone, today, locale)

        assertThat(timeline.itemCount).isEqualTo(10_000)
        val headerCount = timeline.cells.count { it is PhotosCell.DateHeader }
        assertThat(timeline.cells).hasSize(10_000 + headerCount)
        assertThat(timeline.sectionStarts).hasSize(headerCount)
    }
}
