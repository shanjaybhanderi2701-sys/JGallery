package com.appblish.jgallery.feature.photos

import com.appblish.jgallery.core.model.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One cell of the time-grouped Photos stream: a full-width date header or a media tile. Keys are
 * stable across incremental index updates so the LazyVerticalGrid reuses (never re-composes or
 * re-decodes) unchanged rows — a load-bearing property of the 10k-item scroll gate.
 */
sealed interface PhotosCell {
    val key: String

    data class DateHeader(val label: String, val epochDay: Long) : PhotosCell {
        override val key: String get() = "header:$epochDay"
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
    val itemCount: Int,
) {
    fun bubbleLabel(cellIndex: Int, collapsed: Boolean): String? {
        val labels = if (collapsed) yearByCell else monthYearByCell
        return labels.getOrNull(cellIndex.coerceIn(0, labels.size - 1))
    }
}

/**
 * Group [items] into the date-sectioned stream. Ordering uses the moment the media was CAPTURED
 * ([MediaItem.dateTakenMillis], falling back to file modification time when the metadata is absent),
 * newest first, with the id as a deterministic tiebreak. Header labels per design §1:
 * "Today" / "Yesterday" / "DD/MM/YYYY".
 */
fun buildPhotosTimeline(
    items: List<MediaItem>,
    zone: ZoneId,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): PhotosTimeline {
    val sorted = items.sortedWith(
        compareByDescending<MediaItem> { it.effectiveTimeMillis }.thenBy { it.id.value },
    )

    val headerFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val yesterday = today.minusDays(1)

    val cells = ArrayList<PhotosCell>(sorted.size + 64)
    val sectionStarts = ArrayList<Int>(64)
    val monthYearByCell = ArrayList<String>(sorted.size + 64)
    val yearByCell = ArrayList<String>(sorted.size + 64)

    var currentEpochDay = Long.MIN_VALUE
    // Bubble labels repeat for every cell in a month/year; format once per section, share the string.
    var monthLabel = ""
    var yearLabel = ""

    for (item in sorted) {
        val date = Instant.ofEpochMilli(item.effectiveTimeMillis).atZone(zone).toLocalDate()
        if (date.toEpochDay() != currentEpochDay) {
            currentEpochDay = date.toEpochDay()
            monthLabel = date.format(monthFormat)
            yearLabel = date.year.toString()
            val label = when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> date.format(headerFormat)
            }
            sectionStarts += cells.size
            cells += PhotosCell.DateHeader(label = label, epochDay = currentEpochDay)
            monthYearByCell += monthLabel
            yearByCell += yearLabel
        }
        cells += PhotosCell.Tile(item)
        monthYearByCell += monthLabel
        yearByCell += yearLabel
    }

    return PhotosTimeline(
        cells = cells,
        sectionStarts = sectionStarts,
        monthYearByCell = monthYearByCell,
        yearByCell = yearByCell,
        itemCount = sorted.size,
    )
}

/** Capture time when known; file-modified time when the row carries no date-taken metadata. */
internal val MediaItem.effectiveTimeMillis: Long
    get() = if (dateTakenMillis > 0) dateTakenMillis else dateModifiedMillis

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
