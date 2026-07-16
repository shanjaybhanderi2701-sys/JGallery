package com.appblish.jgallery.feature.photos

import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.ui.grid.FastScrollMath
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One cell of the time-grouped Photos stream: a full-width section header or a media tile. Keys are
 * stable across incremental index updates so the LazyVerticalGrid reuses (never re-composes or
 * re-decodes) unchanged rows — a load-bearing property of the 10k-item scroll gate.
 */
sealed interface PhotosCell {
    val key: String

    /**
     * A full-width, **sticky** section header (design G1-10). [sectionKey] is the grouping-key value
     * for the run beneath it — epoch-day for [GroupBy.DAY], year·12+month for [GroupBy.MONTH], year
     * for [GroupBy.YEAR] — so the key is unique per section and stable across incremental updates.
     */
    data class DateHeader(val label: String, val sectionKey: Long) : PhotosCell {
        override val key: String get() = "header:$sectionKey"
    }

    data class Tile(val item: MediaItem) : PhotosCell {
        override val key: String get() = item.id.value
    }
}

/**
 * The fully-precomputed Photos stream (spec §4, design a05/a07/a14). Built ONCE per index emission on
 * a background dispatcher — the composable reads plain lists and never does per-frame work:
 *
 * - [cells]: headers + tiles, newest-first.
 * - [sectionStarts]: indices of the headers — the fast-scroll release snap points.
 * - [bubbleLabel]: fast-scroll bubble text for a cell ("Month YYYY", or "YYYY" collapsed at speed).
 */
class PhotosTimeline(
    val cells: List<PhotosCell>,
    val sectionStarts: List<Int>,
    private val monthYearByCell: List<String>,
    private val yearByCell: List<String>,
    private val itemOrdinalByCell: List<Int>,
    val itemCount: Int,
    private val locale: Locale = Locale.getDefault(),
    private val sortKey: SortKey = SortKey.LAST_MODIFIED,
    private val sizeLabelByCell: List<String> = emptyList(),
    private val nameLetterByCell: List<String> = emptyList(),
) {
    /**
     * Context-aware fast-scroll bubble text for a cell (APP-496 item 7): the readout matches the
     * active **Sort** dimension — the axis the stream is actually ordered along — the way Google
     * Photos / Samsung Gallery show the letter when sorted A–Z and the size when sorted by size:
     *
     * - [SortKey.LAST_MODIFIED] → "Month YYYY · item N of TOTAL" (terse "YYYY" when [collapsed]),
     *   the design W3-09 at-scale date readout. Unchanged from before.
     * - [SortKey.FILE_SIZE] → the file size at that position ("4.2 MB · item N of TOTAL"; terse size
     *   when [collapsed]).
     * - [SortKey.FILE_NAME] / [SortKey.FILE_PATH] → the leading letter at that position ("A"), the
     *   A–Z index reader.
     *
     * All labels come straight from the precomputed per-cell arrays, so dragging a 61,908-item folder
     * never rescans.
     */
    fun bubbleLabel(cellIndex: Int, collapsed: Boolean): String? = when (sortKey) {
        SortKey.FILE_SIZE -> {
            val size = sizeLabelByCell.getOrNull(cellIndex.coerceIn(0, sizeLabelByCell.size - 1)) ?: return null
            if (collapsed) size else withPosition(size, cellIndex)
        }
        SortKey.FILE_NAME, SortKey.FILE_PATH ->
            nameLetterByCell.getOrNull(cellIndex.coerceIn(0, nameLetterByCell.size - 1))
        SortKey.LAST_MODIFIED -> {
            if (collapsed) {
                yearByCell.getOrNull(cellIndex.coerceIn(0, yearByCell.size - 1))
            } else {
                val month = monthYearByCell.getOrNull(cellIndex.coerceIn(0, monthYearByCell.size - 1))
                    ?: return null
                withPosition(month, cellIndex)
            }
        }
    }

    /** Append the "· item N of TOTAL" at-scale position suffix (W3-09) to [primary]. */
    private fun withPosition(primary: String, cellIndex: Int): String {
        val ordinal = itemOrdinalByCell.getOrNull(cellIndex.coerceIn(0, itemOrdinalByCell.size - 1)) ?: return primary
        val position = FastScrollMath.formatItemPosition(ordinal, itemCount, locale) ?: return primary
        return "$primary · $position"
    }
}

/**
 * Group [items] into the time-sectioned stream. Ordering uses the moment the media was CAPTURED
 * ([MediaItem.dateTakenMillis], falling back to file modification time when the metadata is absent),
 * newest first, with the id as a deterministic tiebreak.
 *
 * [groupBy] chooses the sectioning dimension (design G1-10), composing on top of the already-applied
 * filter — the same sorted stream is re-sectioned with no rescan:
 * - [GroupBy.DAY] — "Today" / "Yesterday" / "dd/MM/yyyy" (the default).
 * - [GroupBy.MONTH] — "MMMM yyyy".
 * - [GroupBy.YEAR] — "yyyy".
 * - [GroupBy.NONE] — one flat, header-less grid ([sectionStarts] is empty).
 *
 * The fast-scroll bubble stays month/year regardless of grouping, so the readout is useful even when
 * the grid itself carries no headers.
 */
fun buildPhotosTimeline(
    items: List<MediaItem>,
    zone: ZoneId,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
    groupBy: GroupBy = GroupBy.DAY,
    sort: SortSpec = SortSpec(),
): PhotosTimeline {
    // Composition order (Grouping.kt): Filter → Sort → Group. The active sort orders the stream; the
    // section run below is then cut over that order. The default (Last Modified, descending) reduces
    // to the previous hard-coded capture-time ordering, so it is byte-identical to before.
    val sorted = items.sortedWith(sort.timelineComparator())

    val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val yesterday = today.minusDays(1)

    val cells = ArrayList<PhotosCell>(sorted.size + 64)
    val sectionStarts = ArrayList<Int>(64)
    val monthYearByCell = ArrayList<String>(sorted.size + 64)
    val yearByCell = ArrayList<String>(sorted.size + 64)
    // 1-based item position (headers excluded) for the W3-09 "item N of TOTAL" bubble readout. A header
    // cell carries the ordinal of the first item beneath it, so a drag that lands on a section boundary
    // reports where that section begins.
    val itemOrdinalByCell = ArrayList<Int>(sorted.size + 64)
    // Context-aware bubble dimensions (APP-496 item 7): the file size and the leading letter at each
    // cell, precomputed so a size- or name-sorted drag reads the right axis with no per-frame work. A
    // header cell inherits the value of the first item beneath it.
    val sizeLabelByCell = ArrayList<String>(sorted.size + 64)
    val nameLetterByCell = ArrayList<String>(sorted.size + 64)

    var currentSectionKey = Long.MIN_VALUE
    var itemsSoFar = 0
    // Bubble labels repeat for every cell in a month/year; format once per section, share the string.
    var monthLabel = ""
    var yearLabel = ""

    for (item in sorted) {
        val date = Instant.ofEpochMilli(item.effectiveTimeMillis).atZone(zone).toLocalDate()
        monthLabel = date.format(monthFormat)
        yearLabel = date.year.toString()
        val sizeLabel = FastScrollMath.formatByteSize(item.sizeBytes, locale)
        val nameLetter = leadingLetter(item.displayName, locale)
        // NONE emits no headers: every cell shares one sentinel section so a header is never opened.
        val sectionKey = if (groupBy == GroupBy.NONE) 0L else sectionKeyFor(date, groupBy)
        if (groupBy != GroupBy.NONE && sectionKey != currentSectionKey) {
            currentSectionKey = sectionKey
            val label = when (groupBy) {
                GroupBy.DAY -> when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> date.format(dayFormat)
                }
                GroupBy.MONTH -> monthLabel
                GroupBy.YEAR -> yearLabel
                GroupBy.NONE -> "" // unreachable — NONE never opens a section
            }
            sectionStarts += cells.size
            cells += PhotosCell.DateHeader(label = label, sectionKey = sectionKey)
            monthYearByCell += monthLabel
            yearByCell += yearLabel
            itemOrdinalByCell += itemsSoFar + 1
            sizeLabelByCell += sizeLabel
            nameLetterByCell += nameLetter
        }
        itemsSoFar++
        cells += PhotosCell.Tile(item)
        monthYearByCell += monthLabel
        yearByCell += yearLabel
        itemOrdinalByCell += itemsSoFar
        sizeLabelByCell += sizeLabel
        nameLetterByCell += nameLetter
    }

    return PhotosTimeline(
        cells = cells,
        sectionStarts = sectionStarts,
        monthYearByCell = monthYearByCell,
        yearByCell = yearByCell,
        itemOrdinalByCell = itemOrdinalByCell,
        itemCount = sorted.size,
        locale = locale,
        sortKey = sort.key,
        sizeLabelByCell = sizeLabelByCell,
        nameLetterByCell = nameLetterByCell,
    )
}

/**
 * Leading A–Z letter for the name-sorted fast-scroll bubble (APP-496 item 7). The **first** character
 * of the name upper-cased when it is a letter; anything else (a name starting with a digit or symbol,
 * or an empty name) buckets to "#", matching how contact/gallery A–Z indexes read — and matching the
 * name comparator, which orders on the whole lower-cased name so a digit-leading name sorts ahead of A.
 */
private fun leadingLetter(displayName: String, locale: Locale = Locale.getDefault()): String {
    val first = displayName.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.toString().uppercase(locale) else "#"
}

/**
 * The section-boundary key for [date] under [groupBy] — a header opens whenever this value changes
 * between adjacent (newest-first) items. Values are monotonic in calendar time so the newest-first
 * stream yields newest-first sections, and unique per section so the header cell key is stable.
 */
private fun sectionKeyFor(date: LocalDate, groupBy: GroupBy): Long = when (groupBy) {
    GroupBy.DAY -> date.toEpochDay()
    GroupBy.MONTH -> date.year.toLong() * 12L + (date.monthValue - 1)
    GroupBy.YEAR -> date.year.toLong()
    GroupBy.NONE -> 0L
}

/** Capture time when known; file-modified time when the row carries no date-taken metadata. */
internal val MediaItem.effectiveTimeMillis: Long
    get() = if (dateTakenMillis > 0) dateTakenMillis else dateModifiedMillis

/**
 * Ordering for the Photos stream keyed by the active [SortSpec] (design G1-D7 §3). Mirrors the
 * cached-index comparator so the four Sort-By keys behave identically to Albums — except Last
 * Modified uses [effectiveTimeMillis] (capture-time-preferring) so the default day/month/year
 * sectioning still groups by the moment a photo was taken. The id is a deterministic total-order
 * tiebreak so the grid keys stay stable across incremental updates.
 */
private fun SortSpec.timelineComparator(): Comparator<MediaItem> {
    val ascending: Comparator<MediaItem> = when (key) {
        SortKey.FILE_NAME -> compareBy { it.displayName.lowercase() }
        SortKey.FILE_SIZE -> compareBy { it.sizeBytes }
        SortKey.LAST_MODIFIED -> compareBy { it.effectiveTimeMillis }
        // MediaItem carries no path yet; fall back to name so the sort stays total, matching the index.
        SortKey.FILE_PATH -> compareBy { it.displayName.lowercase() }
    }
    val directed = if (direction == SortDirection.DESCENDING) ascending.reversed() else ascending
    return directed.thenBy { it.id.value }
}

/** "1:05" / "12:07" / "1:23:45" — the video duration badge on grid tiles. */
internal fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
