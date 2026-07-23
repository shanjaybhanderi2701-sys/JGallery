package com.appblish.jgallery.core.ui.grouping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Box

/**
 * Shared time-sectioning for any media grid (design G1-10 / spec §4 "Group by"). Lives in `:core:ui`
 * so the Photos tab **and** every album section the grid the same way — the [GroupBy] control on the
 * shared overflow menu behaves identically wherever it is surfaced (APP-499: "grouping must be
 * available inside albums too, and cannot drift from home").
 *
 * The Photos tab keeps its own richer `PhotosTimeline` (it additionally precomputes the at-scale
 * fast-scroll bubble strings for the 10k gate); this is the lean grouping other grids reuse.
 */
sealed interface MediaCell {
    val key: String

    /** A full-width, **sticky** section header. [sectionKey] is unique + stable per section run. */
    data class Header(val label: String, val sectionKey: Long) : MediaCell {
        override val key: String get() = "media_header:$sectionKey"
    }

    data class Tile(val item: MediaItem) : MediaCell {
        override val key: String get() = item.id.value
    }
}

/** Precomputed grouped stream: header+tile [cells] and the header indices ([sectionStarts]). */
class MediaSections(
    val cells: List<MediaCell>,
    val sectionStarts: List<Int>,
) {
    /** Resolve a grid cell index to the media id beneath it — null for header cells. */
    fun idAt(index: Int): String? = (cells.getOrNull(index) as? MediaCell.Tile)?.item?.id?.value
}

/**
 * Section [items] (already filtered + sorted) into the time-grouped stream. Mirrors the Photos
 * timeline's labels exactly (design G1-10) so a Day/Month/Year grouping reads identically on either
 * surface. [GroupBy.NONE] yields one flat, header-less run ([sectionStarts] empty).
 *
 * The stream is **bucketed** by section key rather than cut over the incoming order, so every section
 * appears exactly once and its items stay contiguous even when the active Sort disagrees with the
 * capture-time grouping key. This is load-bearing: the album grid sorts by `dateModified` (or name /
 * size) while grouping by capture time, and an exported set can share one modified time yet span
 * several capture days — cutting sections over that order would re-open a day and reuse its
 * `media_header:<key>` LazyGrid key, which is a hard duplicate-key crash (APP-609). Sections keep the
 * incoming stream's first-encounter order; items keep their intra-section Sort order.
 */
fun buildMediaSections(
    items: List<MediaItem>,
    groupBy: GroupBy,
    zone: ZoneId,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): MediaSections {
    if (groupBy == GroupBy.NONE || items.isEmpty()) {
        return MediaSections(cells = items.map { MediaCell.Tile(it) }, sectionStarts = emptyList())
    }

    val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val yesterday = today.minusDays(1)

    // Bucket by section key, preserving first-encounter order (LinkedHashMap) so the section order
    // still follows the incoming stream's direction, while every same-key item lands in one run.
    val grouped = LinkedHashMap<Long, MutableList<MediaItem>>()
    for (item in items) {
        val date = Instant.ofEpochMilli(item.effectiveTimeMillis).atZone(zone).toLocalDate()
        grouped.getOrPut(sectionKeyFor(date, groupBy)) { ArrayList() }.add(item)
    }

    val cells = ArrayList<MediaCell>(items.size + grouped.size)
    val sectionStarts = ArrayList<Int>(grouped.size)

    for ((sectionKey, sectionItems) in grouped) {
        // All items in a bucket share the section key, hence the same day/month/year — take the label
        // from the first one.
        val date = Instant.ofEpochMilli(sectionItems.first().effectiveTimeMillis).atZone(zone).toLocalDate()
        val label = when (groupBy) {
            GroupBy.DAY -> when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> date.format(dayFormat)
            }
            GroupBy.MONTH -> date.format(monthFormat)
            GroupBy.YEAR -> date.year.toString()
            GroupBy.NONE -> "" // unreachable — NONE returns early above
        }
        sectionStarts += cells.size
        cells += MediaCell.Header(label = label, sectionKey = sectionKey)
        for (item in sectionItems) cells += MediaCell.Tile(item)
    }
    return MediaSections(cells = cells, sectionStarts = sectionStarts)
}

/** Monotonic section-boundary key for [date] under [groupBy] (matches the Photos timeline). */
private fun sectionKeyFor(date: LocalDate, groupBy: GroupBy): Long = when (groupBy) {
    GroupBy.DAY -> date.toEpochDay()
    GroupBy.MONTH -> date.year.toLong() * 12L + (date.monthValue - 1)
    GroupBy.YEAR -> date.year.toLong()
    GroupBy.NONE -> 0L
}

/** Capture time when known; file-modified time when the row carries no date-taken metadata. */
private val MediaItem.effectiveTimeMillis: Long
    get() = if (dateTakenMillis > 0) dateTakenMillis else dateModifiedMillis

/**
 * A full-width group header (design G1-10). Shared by the inline header cells and the pinned
 * [StickyMediaHeader] overlay so they render identically as one scrolls under the other.
 */
@Composable
fun GroupSectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall,
        // Theme token so the shared header re-themes in Dark alongside the Photos tab (APP-572).
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

/**
 * The sticky section header (design G1-10): the label of the section currently under the top of the
 * viewport, pinned over an opaque background so tiles scroll beneath it; the next section's inline
 * header pushes it up 1:1 as it climbs in. Renders nothing for [GroupBy.NONE] (no [sectionStarts]) or
 * before first layout. Same pattern the Photos tab uses, so the two surfaces behave identically.
 */
@Composable
fun BoxScope.StickyMediaHeader(
    gridState: LazyGridState,
    sections: MediaSections,
    testTag: String = "media_sticky_header",
) {
    val sectionStarts = sections.sectionStarts
    if (sectionStarts.isEmpty()) return

    var headerHeightPx by remember { mutableIntStateOf(0) }

    val pinned by remember(sections) {
        derivedStateOf {
            val firstIndex = gridState.firstVisibleItemIndex
            val currentHeaderCell = sectionStarts.lastOrNull { it <= firstIndex } ?: return@derivedStateOf null
            val label = (sections.cells.getOrNull(currentHeaderCell) as? MediaCell.Header)?.label
                ?: return@derivedStateOf null
            val nextHeaderCell = sectionStarts.firstOrNull { it > firstIndex }
            val nextTop = nextHeaderCell?.let { cell ->
                gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == cell }?.offset?.y
            }
            val push = if (nextTop != null && nextTop < headerHeightPx) nextTop - headerHeightPx else 0
            PinnedHeader(label, push)
        }
    }

    pinned?.let { header ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = header.pushPx.toFloat() }
                .onSizeChanged { headerHeightPx = it.height }
                // Opaque pinned band follows the theme so it doesn't stay light-on-dark (APP-572).
                .background(MaterialTheme.colorScheme.background)
                .testTag(testTag),
        ) {
            GroupSectionHeader(label = header.label)
        }
    }
}

private data class PinnedHeader(val label: String, val pushPx: Int)
