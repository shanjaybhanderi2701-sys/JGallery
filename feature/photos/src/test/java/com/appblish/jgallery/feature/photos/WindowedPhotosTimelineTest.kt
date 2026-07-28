package com.appblish.jgallery.feature.photos

import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Golden-equivalence §4.6 gate (APP-704): [WindowedPhotosTimeline.fromItems] must produce the same
 * section structure — headers, ordinals, section counts — as [buildPhotosTimeline] for all groupBy
 * modes and a representative item set. This test is the correctness gate that allows the windowed
 * path to replace the legacy fully-materialized path.
 *
 * Also tests the windowed cell API independently: placeholder behavior, key stability, bubbleLabel
 * for time sort from skeleton (no window cache needed), and null cells for unloaded windows.
 */
class WindowedPhotosTimelineTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 7, 9)
    private val locale = Locale.UK

    // ── Fixture ──────────────────────────────────────────────────────────────────────────────────

    /** 10 items spread across 3 distinct calendar days: days 0, 0, 1, 1, 1, 2, 2, 2, 2, 2. */
    private val items: List<MediaItem> = run {
        val days = listOf(0, 0, 1, 1, 1, 2, 2, 2, 2, 2)
        days.mapIndexed { i, dayOffset ->
            val ms = today.minusDays(dayOffset.toLong())
                .atStartOfDay(zone).toInstant().toEpochMilli() + i
            MediaItem(
                id = MediaId("item_$i"),
                displayName = "IMG_$i.jpg",
                type = MediaType.IMAGE,
                bucketId = "b",
                bucketName = "Camera",
                dateTakenMillis = ms,
                dateModifiedMillis = ms,
                sizeBytes = (i + 1) * 1_000L,
                width = 100, height = 100, durationMillis = 0, mimeType = "image/jpeg",
            )
        }
    }

    // ── §4.6 golden-equivalence ──────────────────────────────────────────────────────────────────

    @Test
    fun `DAY grouping - section structure matches buildPhotosTimeline`() {
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.DAY)
        val legacy = buildPhotosTimeline(items, zone, today, locale, GroupBy.DAY)

        assertSectionEquivalence(windowed, legacy)
    }

    @Test
    fun `MONTH grouping - section structure matches buildPhotosTimeline`() {
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.MONTH)
        val legacy = buildPhotosTimeline(items, zone, today, locale, GroupBy.MONTH)

        assertSectionEquivalence(windowed, legacy)
    }

    @Test
    fun `YEAR grouping - section structure matches buildPhotosTimeline`() {
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.YEAR)
        val legacy = buildPhotosTimeline(items, zone, today, locale, GroupBy.YEAR)

        assertSectionEquivalence(windowed, legacy)
    }

    @Test
    fun `NONE grouping - section structure matches buildPhotosTimeline`() {
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.NONE)
        val legacy = buildPhotosTimeline(items, zone, today, locale, GroupBy.NONE)

        assertThat(windowed.itemCount).isEqualTo(legacy.itemCount)
        assertThat(windowed.sectionStarts).isEmpty()
        assertThat(legacy.sectionStarts).isEmpty()
        assertThat(windowed.totalCells).isEqualTo(items.size)
    }

    private fun assertSectionEquivalence(
        windowed: WindowedPhotosTimeline,
        legacy: PhotosTimeline,
    ) {
        // Item counts must agree.
        assertThat(windowed.itemCount).isEqualTo(legacy.itemCount)
        // Section header cell positions must agree.
        assertThat(windowed.sectionStarts).isEqualTo(legacy.sectionStarts)
        // Total cells must agree (items + headers).
        assertThat(windowed.totalCells).isEqualTo(legacy.cells.size)
        // Header labels must agree in order.
        val windowedHeaders = (0 until windowed.totalCells).mapNotNull {
            windowed.cellAt(it) as? PhotosCell.DateHeader
        }
        val legacyHeaders = legacy.cells.filterIsInstance<PhotosCell.DateHeader>()
        assertThat(windowedHeaders.map { it.label }).isEqualTo(legacyHeaders.map { it.label })
        assertThat(windowedHeaders.map { it.sectionKey }).isEqualTo(legacyHeaders.map { it.sectionKey })
    }

    // ── Cell API tests ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `cellAt returns null for tile cells when window cache is empty`() {
        // A timeline with no windowCache — all tile cells return null (placeholder).
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.DAY)
        // Rebuild the same timeline but with an empty cache to simulate unloaded windows.
        val emptyCache = WindowedPhotosTimeline(
            skeleton = windowed.skeleton,
            windowCache = emptyMap(),
            zone = zone,
            today = today,
            locale = locale,
            sort = SortSpec(),
            groupBy = GroupBy.DAY,
        )
        // Headers still resolve (they come from the skeleton, not the cache).
        val headerCells = emptyCache.sectionStarts
        headerCells.forEach { idx ->
            assertThat(emptyCache.cellAt(idx)).isInstanceOf(PhotosCell.DateHeader::class.java)
        }
        // Tile cells return null.
        val tileCells = (0 until emptyCache.totalCells).filter { it !in headerCells }
        tileCells.forEach { idx ->
            assertThat(emptyCache.cellAt(idx)).isNull()
        }
    }

    @Test
    fun `keyAt is stable - same key for the same ordinal across two identical fromItems calls`() {
        val a = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.DAY)
        val b = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.DAY)
        for (i in 0 until a.totalCells) {
            assertThat(a.keyAt(i)).isEqualTo(b.keyAt(i))
        }
    }

    @Test
    fun `bubbleLabel for LAST_MODIFIED sort is derived from skeleton - no window cache required`() {
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.DAY)
        val emptyCache = WindowedPhotosTimeline(
            skeleton = windowed.skeleton,
            windowCache = emptyMap(),   // cache intentionally empty
            zone = zone,
            today = today,
            locale = locale,
            sort = SortSpec(),          // LAST_MODIFIED, DESCENDING (default)
            groupBy = GroupBy.DAY,
        )
        // bubbleLabel for the first header cell returns a non-null month/year string even with no cache.
        val headerCell = emptyCache.sectionStarts.first()
        val label = emptyCache.bubbleLabel(headerCell, collapsed = false)
        assertThat(label).isNotNull()
        assertThat(label).contains("2026") // all items are in 2026
    }

    @Test
    fun `NONE grouping - cell index equals ordinal and all cells are tiles`() {
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.NONE)
        assertThat(windowed.totalCells).isEqualTo(items.size)
        for (i in 0 until windowed.totalCells) {
            assertThat(windowed.contentTypeAt(i)).isEqualTo("media_tile")
            assertThat(windowed.itemOrdinalAt(i)).isEqualTo(i)
        }
    }

    @Test
    fun `DAY grouping - tile ordinals are correct across headers`() {
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.DAY)
        var expectedOrdinal = 0
        for (i in 0 until windowed.totalCells) {
            when (windowed.contentTypeAt(i)) {
                "date_header" -> {
                    // Header cell carries null ordinal.
                    assertThat(windowed.itemOrdinalAt(i)).isNull()
                }
                "media_tile" -> {
                    assertThat(windowed.itemOrdinalAt(i)).isEqualTo(expectedOrdinal)
                    expectedOrdinal++
                }
            }
        }
        assertThat(expectedOrdinal).isEqualTo(items.size)
    }

    @Test
    fun `itemById finds items across window cache`() {
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.DAY)
        // All items are in windowCache[0] from fromItems.
        items.forEach { item ->
            assertThat(windowed.itemById(item.id)).isEqualTo(item)
        }
        assertThat(windowed.itemById(MediaId("nonexistent"))).isNull()
    }

    @Test
    fun `tileItemAt returns null for headers and items for tiles`() {
        val windowed = WindowedPhotosTimeline.fromItems(items, zone, today, locale, GroupBy.DAY)
        for (i in 0 until windowed.totalCells) {
            if (windowed.contentTypeAt(i) == "date_header") {
                assertThat(windowed.tileItemAt(i)).isNull()
            } else {
                assertThat(windowed.tileItemAt(i)).isNotNull()
            }
        }
    }
}
