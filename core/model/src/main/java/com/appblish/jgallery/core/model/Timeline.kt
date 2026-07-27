package com.appblish.jgallery.core.model

/**
 * The ordered, filtered, grouped definition of a timeline stream (APP-700, Option B). Everything
 * that determines section layout **and** item order lives here, so the `:core:index` layer can push
 * the whole thing into SQL — sort + window + section skeleton — instead of materializing the library
 * in memory and re-sorting per emission.
 *
 * It is deliberately platform-free (like [MediaQuery]) so a feature can construct one for
 * `MediaIndexRepository.observeTimelineSkeleton` / `loadWindow` without seeing the storage module.
 *
 * - [bucketId] `null` = the whole library (Photos tab); set = a single album.
 * - [types] restricts by media kind (the default is the Photos "images + videos" set).
 * - [filter] is the format chip (ALL/PHOTOS/VIDEOS/GIFS) — pushed into the SQL WHERE (APP-704 D5) so
 *   window offsets count the *filtered* set, not the whole table.
 * - [sort] is the authority for order; the SQL layer sorts by it (APP-704 D2). No in-memory re-sort.
 * - [groupBy] defines the SQL section aggregation (day/month/year/none).
 * - [ids] restricts to specific ids (the Favorites smart view reuses this); `null` = no restriction,
 *   an empty set matches nothing — same contract as [MediaQuery.ids].
 */
data class TimelineSpec(
    val bucketId: String? = null,
    val types: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO),
    val filter: MediaFilter = MediaFilter.ALL,
    val sort: SortSpec = SortSpec(),
    val groupBy: GroupBy = GroupBy.DAY,
    val ids: Set<MediaId>? = null,
)

/**
 * The light structural skeleton of a timeline — a handful of rows, never N. From it the feature layer
 * derives header cells, `sectionStarts`, the per-cell ordinal and the fast-scroll bubble
 * **arithmetically** (O(#sections) memory, O(1)/O(log #sections) lookup) instead of holding a
 * fully-materialized `List<PhotosCell>` and nine parallel N-length arrays.
 *
 * [totalItems] is the count of items in the (filtered) stream — the "N" of the "item N of TOTAL"
 * bubble and the value that clamps window offsets. [sections] are the contiguous runs, in display
 * order, that each render as one sticky header + its tiles.
 */
data class TimelineSkeleton(
    val totalItems: Int,
    val sections: List<SectionRun>,
) {
    companion object {
        val EMPTY = TimelineSkeleton(totalItems = 0, sections = emptyList())
    }
}

/**
 * One contiguous section run in a [TimelineSkeleton].
 *
 * - [sectionKey] is the grouping-key value for the run (epoch-day for DAY, `year*12+month` for MONTH,
 *   `year` for YEAR, `0` for NONE) — unique + stable per section, so the header cell key
 *   (`"header:$sectionKey"`) survives incremental updates (invariant I4). Under a non-time sort the
 *   same calendar section can appear in **multiple** non-contiguous runs (APP-704 F2/D3), so the key
 *   is not globally unique in that case — the run's [firstItemOrdinal] disambiguates the header cell.
 * - [dayKey] is the epoch-day (in the sync-time zone) of the run's first item — enough for the feature
 *   layer to format the DAY/MONTH/YEAR label (Today/Yesterday/dd·MM·yyyy / "MMMM yyyy" / "yyyy").
 * - [firstItemOrdinal] is the 0-based item ordinal (headers excluded) of the run's first tile.
 * - [count] is the number of tiles under the run.
 */
data class SectionRun(
    val sectionKey: Long,
    val dayKey: Long,
    val firstItemOrdinal: Int,
    val count: Int,
)
