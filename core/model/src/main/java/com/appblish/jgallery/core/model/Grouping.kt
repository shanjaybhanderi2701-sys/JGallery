package com.appblish.jgallery.core.model

/**
 * Time-sectioning dimension for the Photos stream (design G1-10 / spec §4 "Group by"). Sections the
 * already-filtered, sorted grid into runs that render as **sticky** headers:
 *
 * - [DAY] — "Today" / "Yesterday" / "dd/MM/yyyy" (the default, matching the round-1 timeline).
 * - [MONTH] — "MMMM yyyy".
 * - [YEAR] — "yyyy".
 * - [NONE] — one flat, header-less grid.
 *
 * Independent of [SortSpec] and [MediaFilter]: the composition order is Filter (subset) → Sort
 * (order) → Group-by (sectioning), so re-grouping never re-scans or re-sorts the cached index.
 */
enum class GroupBy { DAY, MONTH, YEAR, NONE }
