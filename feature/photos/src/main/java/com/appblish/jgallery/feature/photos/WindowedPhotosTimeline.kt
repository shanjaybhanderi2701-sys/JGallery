package com.appblish.jgallery.feature.photos

import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.SectionRun
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.TimelineSkeleton
import com.appblish.jgallery.core.ui.grid.FastScrollMath
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Grid structure for the Photos tab backed by [TimelineSkeleton] (APP-700 Option B, L2). Cell
 * geometry — headers, ordinals, [sectionStarts] — is derived arithmetically from the skeleton in
 * O(#sections) memory; tile payloads come from [windowCache], a small LRU map of page-offset →
 * items loaded by [PhotosViewModel.loadWindowFor] as the user scrolls.
 *
 * All public read paths are O(1) or O(log #sections) — no N-length per-cell arrays.
 *
 * The grid address model (groupBy != NONE):
 * - Header of section *s* lives at cell index `sections[s].firstItemOrdinal + s`.
 * - Tile at ordinal *o* lives at cell index `o + ip + 1`, where *ip* is the number of headers that
 *   come before the cell (derived by [upperBound] on [sectionStarts]).
 * - [totalCells] = totalItems + sections.size.
 *
 * For [GroupBy.NONE] there are no headers: cell index == ordinal, [totalCells] == [itemCount].
 */
class WindowedPhotosTimeline(
    val skeleton: TimelineSkeleton,
    val windowCache: Map<Int, List<MediaItem>>,
    private val zone: ZoneId,
    private val today: LocalDate,
    private val locale: Locale,
    private val sort: SortSpec,
    private val groupBy: GroupBy,
) {
    /** Total number of item tiles (section headers excluded). */
    val itemCount: Int get() = skeleton.totalItems

    /**
     * Cell indices of section headers in ascending order. Empty when [groupBy] is [GroupBy.NONE].
     * The [GridFastScroller] uses these as release-snap points; [StickyGroupHeader] uses them to
     * locate the current section label.
     */
    val sectionStarts: List<Int> = buildList {
        if (groupBy != GroupBy.NONE) {
            skeleton.sections.forEachIndexed { si, section ->
                add(section.firstItemOrdinal + si)
            }
        }
    }

    /** Total cells in the lazy grid: items + one header per section (zero headers for NONE). */
    val totalCells: Int =
        if (groupBy == GroupBy.NONE) skeleton.totalItems
        else skeleton.totalItems + skeleton.sections.size

    // ── Formatters (allocated once per timeline instance) ───────────────────────────────────────

    private val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val yesterday: LocalDate = today.minusDays(1)

    // ── Grid address helpers ─────────────────────────────────────────────────────────────────────

    /** True if [cellIndex] is a section header (i.e. it appears in [sectionStarts]). O(log N). */
    private fun isHeader(cellIndex: Int): Boolean {
        if (sectionStarts.isEmpty()) return false
        return sectionStarts.binarySearch(cellIndex) >= 0
    }

    /**
     * Index into [skeleton.sections] for the header at [cellIndex]. Returns a non-negative value
     * only when [isHeader] is true; the result is undefined otherwise.
     */
    private fun sectionIndexAt(cellIndex: Int): Int = sectionStarts.binarySearch(cellIndex)

    /**
     * 0-based item ordinal for a **tile** [cellIndex]. Undefined for header cells — callers must
     * guard with [isHeader] first. O(log #sections).
     */
    private fun cellToOrdinal(cellIndex: Int): Int {
        if (groupBy == GroupBy.NONE) return cellIndex
        // upperBound returns the count of sectionStarts ≤ cellIndex. For a tile (not in
        // sectionStarts) this equals the count of headers strictly before this cell.
        val headersUpTo = sectionStarts.upperBound(cellIndex)
        return cellIndex - headersUpTo
    }

    /**
     * The [SectionRun] that contains item [ordinal] (O(log #sections)), or null when there are no
     * sections (GroupBy.NONE or an empty skeleton).
     */
    private fun sectionForOrdinal(ordinal: Int): SectionRun? {
        val sections = skeleton.sections
        if (sections.isEmpty()) return null
        // Binary search for the rightmost section whose firstItemOrdinal ≤ ordinal.
        var lo = 0; var hi = sections.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (sections[mid].firstItemOrdinal <= ordinal) lo = mid else hi = mid - 1
        }
        return sections[lo]
    }

    // ── Public cell API ──────────────────────────────────────────────────────────────────────────

    /**
     * Stable key for [cellIndex] used as the [LazyVerticalGrid] item key. Headers include the
     * [SectionRun.firstItemOrdinal] suffix so that non-monotonic sections — the same calendar
     * section appearing in multiple non-contiguous runs under a name/size sort (APP-704 D3) — get
     * distinct keys, satisfying key-stability invariant I4.
     */
    fun keyAt(cellIndex: Int): String {
        if (groupBy != GroupBy.NONE && isHeader(cellIndex)) {
            val sec = skeleton.sections[sectionIndexAt(cellIndex)]
            return "header:${sec.sectionKey}:${sec.firstItemOrdinal}"
        }
        return "tile:${cellToOrdinal(cellIndex)}"
    }

    /** "date_header" for section headers, "media_tile" for everything else. */
    fun contentTypeAt(cellIndex: Int): String =
        if (groupBy != GroupBy.NONE && isHeader(cellIndex)) "date_header" else "media_tile"

    /**
     * The rendered cell at [cellIndex]:
     * - [PhotosCell.DateHeader] — label derived from the skeleton (no window cache needed).
     * - [PhotosCell.Tile] — item from [windowCache]; **null** when the window is not yet loaded
     *   (the caller should show a placeholder and trigger [PhotosViewModel.loadWindowFor]).
     */
    fun cellAt(cellIndex: Int): PhotosCell? {
        if (groupBy != GroupBy.NONE && isHeader(cellIndex)) {
            val sec = skeleton.sections[sectionIndexAt(cellIndex)]
            val date = LocalDate.ofEpochDay(sec.dayKey)
            val label = when (groupBy) {
                GroupBy.DAY -> when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> date.format(dayFormat)
                }
                GroupBy.MONTH -> date.format(monthFormat)
                GroupBy.YEAR -> date.year.toString()
                GroupBy.NONE -> "" // unreachable — NONE never opens a section
            }
            return PhotosCell.DateHeader(label = label, sectionKey = sec.sectionKey)
        }
        val ordinal = cellToOrdinal(cellIndex)
        return itemAtOrdinal(ordinal)?.let { PhotosCell.Tile(it) }
    }

    /**
     * The [MediaItem] at tile [cellIndex], or null for headers and for tiles whose window is not
     * yet cached. Used by [ThumbnailPrefetch] to enqueue requests without composing the tile.
     */
    fun tileItemAt(cellIndex: Int): MediaItem? {
        if (groupBy != GroupBy.NONE && isHeader(cellIndex)) return null
        return itemAtOrdinal(cellToOrdinal(cellIndex))
    }

    /** Item ordinal for a tile [cellIndex], or null for headers. */
    fun itemOrdinalAt(cellIndex: Int): Int? {
        if (groupBy != GroupBy.NONE && isHeader(cellIndex)) return null
        return cellToOrdinal(cellIndex)
    }

    /**
     * Linear scan across all cached windows to look up an item by [MediaId]. Used only for the
     * single-item "details" and "singleSelected" reads, which touch at most O(WINDOW_SIZE) items.
     */
    fun itemById(id: MediaId): MediaItem? {
        for ((_, items) in windowCache) {
            val item = items.firstOrNull { it.id == id }
            if (item != null) return item
        }
        return null
    }

    /**
     * Fast-scroll bubble label (APP-496 item 7). For [SortKey.LAST_MODIFIED] the label is derived
     * from the skeleton dayKey — O(log #sections), no window cache needed — so the bubble reads
     * even when tiles aren't loaded. For size/name sorts the item is required; null is returned
     * when the window is not yet cached.
     */
    fun bubbleLabel(cellIndex: Int, collapsed: Boolean): String? {
        // Resolve ordinal: headers report the ordinal of their first tile.
        val ordinal = if (groupBy != GroupBy.NONE && isHeader(cellIndex)) {
            val si = sectionIndexAt(cellIndex)
            skeleton.sections[si].firstItemOrdinal
        } else {
            cellToOrdinal(cellIndex)
        }.coerceIn(0, (itemCount - 1).coerceAtLeast(0))

        return when (sort.key) {
            SortKey.LAST_MODIFIED -> {
                val section = sectionForOrdinal(ordinal) ?: return null
                val date = LocalDate.ofEpochDay(section.dayKey)
                if (collapsed) {
                    date.year.toString()
                } else {
                    val monthYear = date.format(monthFormat)
                    val position = FastScrollMath.formatItemPosition(ordinal + 1, itemCount, locale)
                    if (position != null) "$monthYear · $position" else monthYear
                }
            }
            SortKey.FILE_SIZE -> {
                val item = itemAtOrdinal(ordinal) ?: return null
                val size = FastScrollMath.formatByteSize(item.sizeBytes, locale)
                if (collapsed) size else {
                    val position = FastScrollMath.formatItemPosition(ordinal + 1, itemCount, locale)
                    if (position != null) "$size · $position" else size
                }
            }
            SortKey.FILE_NAME, SortKey.FILE_PATH -> {
                val item = itemAtOrdinal(ordinal) ?: return null
                val first = item.displayName.trim().firstOrNull() ?: return null
                if (first.isLetter()) first.toString().uppercase(locale) else "#"
            }
        }
    }

    // ── Internal window-cache lookup ─────────────────────────────────────────────────────────────

    private fun itemAtOrdinal(ordinal: Int): MediaItem? {
        val windowOffset = (ordinal / WINDOW_SIZE) * WINDOW_SIZE
        return windowCache[windowOffset]?.getOrNull(ordinal - windowOffset)
    }

    // ── Companion / fromItems ────────────────────────────────────────────────────────────────────

    // Public (was internal) so the `benchmark`-variant PhotosBenchmarkActivity layout-only lane in
    // :app can build a windowed timeline from an in-memory corpus after APP-700 changed
    // PhotosUiState.Content to require a WindowedPhotosTimeline (member visibility unchanged: the
    // internal/private helpers below stay module-scoped; only fromItems + WINDOW_SIZE widen).
    companion object {
        /** Number of items per loaded window page — exposed so [PhotosViewModel] and [PhotosScreen] agree. */
        const val WINDOW_SIZE = 120

        /**
         * Builds a [WindowedPhotosTimeline] entirely from an in-memory [items] list. All items are
         * stored as [windowCache][0] so every tile cell is immediately available — used in tests and
         * the golden-equivalence §4.6 gate without depending on `:core:index` internals.
         */
        fun fromItems(
            items: List<MediaItem>,
            zone: ZoneId,
            today: LocalDate,
            locale: Locale = Locale.getDefault(),
            groupBy: GroupBy = GroupBy.DAY,
            sort: SortSpec = SortSpec(),
        ): WindowedPhotosTimeline {
            val sorted = items.sortedWith(effectiveTimeComparator(sort))
            val sections = mutableListOf<SectionRun>()
            var i = 0
            while (i < sorted.size) {
                val item = sorted[i]
                val dayKey = Instant.ofEpochMilli(effectiveTimeMillis(item))
                    .atZone(zone).toLocalDate().toEpochDay()
                val sectionKey = sectionKeyFor(dayKey, groupBy)
                val firstOrdinal = i
                var count = 0
                while (i < sorted.size) {
                    val dk = Instant.ofEpochMilli(effectiveTimeMillis(sorted[i]))
                        .atZone(zone).toLocalDate().toEpochDay()
                    if (sectionKeyFor(dk, groupBy) != sectionKey) break
                    count++
                    i++
                }
                sections += SectionRun(
                    sectionKey = sectionKey,
                    dayKey = dayKey,
                    firstItemOrdinal = firstOrdinal,
                    count = count,
                )
            }
            val skeleton = TimelineSkeleton(
                totalItems = sorted.size,
                sections = if (groupBy == GroupBy.NONE) emptyList() else sections,
            )
            return WindowedPhotosTimeline(
                skeleton = skeleton,
                windowCache = if (sorted.isEmpty()) emptyMap() else mapOf(0 to sorted),
                zone = zone,
                today = today,
                locale = locale,
                sort = sort,
                groupBy = groupBy,
            )
        }

        internal fun effectiveTimeMillis(item: MediaItem): Long =
            if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateModifiedMillis

        internal fun sectionKeyFor(dayKey: Long, groupBy: GroupBy): Long = when (groupBy) {
            GroupBy.DAY -> dayKey
            GroupBy.MONTH -> {
                val d = LocalDate.ofEpochDay(dayKey)
                d.year.toLong() * 12L + (d.monthValue - 1)
            }
            GroupBy.YEAR -> LocalDate.ofEpochDay(dayKey).year.toLong()
            GroupBy.NONE -> 0L
        }

        private fun effectiveTimeComparator(sort: SortSpec): Comparator<MediaItem> {
            val ascending: Comparator<MediaItem> = when (sort.key) {
                SortKey.FILE_NAME -> compareBy { it.displayName.lowercase() }
                SortKey.FILE_SIZE -> compareBy { it.sizeBytes }
                SortKey.LAST_MODIFIED -> compareBy { effectiveTimeMillis(it) }
                // MediaItem carries no path yet; fall back to name so the sort stays total.
                SortKey.FILE_PATH -> compareBy { it.displayName.lowercase() }
            }
            val directed = if (sort.direction == SortDirection.DESCENDING) ascending.reversed() else ascending
            return directed.thenBy { it.id.value }
        }
    }
}

/**
 * Returns the insertion point to the right of any existing occurrences of [target]
 * (upper bound / bisect_right). For a sorted [List<Int>] this equals the count of elements
 * that are ≤ [target] — and, when [target] is not in the list, the count of elements < [target].
 */
private fun List<Int>.upperBound(target: Int): Int {
    var lo = 0; var hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid] <= target) lo = mid + 1 else hi = mid
    }
    return lo
}
