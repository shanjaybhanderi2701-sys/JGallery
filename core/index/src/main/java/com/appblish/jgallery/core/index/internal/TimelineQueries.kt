package com.appblish.jgallery.core.index.internal

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.TimelineSpec

/**
 * Builds the raw SQL for the windowed timeline (APP-700, Option B). Everything that defines order +
 * membership — sort authority (APP-704 D2), format filter (D5), type/bucket/ids restriction — lives
 * in one place so the window, ids and skeleton queries share the exact same WHERE + ORDER and cannot
 * silently disagree.
 *
 * Only genuine runtime data (bucketId, ids, limit, offset) is bound as `?` parameters; the finite
 * enum-derived fragments (media type names, format-bucket name, sort column + direction) are inlined
 * from closed enums, so there is no injection surface.
 */
internal object TimelineQueries {

    /** A viewport page of full rows: `SELECT * … ORDER BY … LIMIT ? OFFSET ?`. */
    fun window(spec: TimelineSpec, offset: Int, limit: Int): SupportSQLiteQuery {
        val args = ArrayList<Any?>()
        val sql = buildString {
            append("SELECT * FROM media")
            append(whereClause(spec, args))
            append(orderClause(spec.sort))
            append(" LIMIT ? OFFSET ?")
        }
        args.add(limit)
        args.add(offset)
        return SimpleSQLiteQuery(sql, args.toArray())
    }

    /** Ids only, in display order — for on-demand select-all / share materialization (D6). */
    fun ids(spec: TimelineSpec): SupportSQLiteQuery {
        val args = ArrayList<Any?>()
        val sql = "SELECT id FROM media" + whereClause(spec, args) + orderClause(spec.sort)
        return SimpleSQLiteQuery(sql, args.toArray())
    }

    /** Day-count skeleton for the time-sort path: `GROUP BY localDayKey`, ordered by day. */
    fun dayCounts(spec: TimelineSpec): SupportSQLiteQuery {
        val args = ArrayList<Any?>()
        val sql = "SELECT localDayKey AS dayKey, COUNT(*) AS itemCount FROM media" +
            whereClause(spec, args) +
            " GROUP BY localDayKey ORDER BY localDayKey ${direction(spec.sort)}"
        return SimpleSQLiteQuery(sql, args.toArray())
    }

    /** Ordered per-item `localDayKey` projection for the name/size-sort skeleton (D3). */
    fun dayKeys(spec: TimelineSpec): SupportSQLiteQuery {
        val args = ArrayList<Any?>()
        val sql = "SELECT localDayKey FROM media" + whereClause(spec, args) + orderClause(spec.sort)
        return SimpleSQLiteQuery(sql, args.toArray())
    }

    /** True when the section skeleton is derivable from the O(#days) `GROUP BY` (monotonic date key). */
    fun isTimeSort(sort: SortSpec): Boolean = sort.key == SortKey.LAST_MODIFIED

    private fun whereClause(spec: TimelineSpec, args: MutableList<Any?>): String {
        val preds = ArrayList<String>()

        // Media kind (IMAGE/VIDEO). Enum names are closed constants → safe to inline.
        preds += if (spec.types.isEmpty()) {
            "0" // no admitted type ⇒ match nothing
        } else {
            "type IN (" + spec.types.joinToString(",") { "'${it.name}'" } + ")"
        }

        spec.bucketId?.let {
            preds += "bucketId = ?"
            args += it
        }

        // Format chip pushed into SQL (D5). ALL = no bucket constraint.
        if (spec.filter != MediaFilter.ALL) {
            preds += "formatBucket = ?"
            args += spec.filter.name
        }

        // ids contract mirrors MediaQuery: null = no restriction; empty = match nothing.
        spec.ids?.let { ids ->
            preds += if (ids.isEmpty()) {
                "0"
            } else {
                ids.forEach { args += it.value }
                "id IN (" + ids.joinToString(",") { "?" } + ")"
            }
        }

        return if (preds.isEmpty()) "" else " WHERE " + preds.joinToString(" AND ")
    }

    /**
     * The sort authority (APP-704 D2): the timeline comparator, not the repo's dead `dateModified`
     * sort. The primary key takes the requested direction; `id ASC` is always the final tiebreak
     * (the Kotlin comparator appends `thenBy { id }` *after* the reversal), so the two orders match.
     */
    private fun orderClause(sort: SortSpec): String {
        val column = when (sort.key) {
            SortKey.LAST_MODIFIED -> "effectiveTimeMillis"
            SortKey.FILE_SIZE -> "sizeBytes"
            // MediaItem carries no path yet → name key, matching the timeline comparator's fallback.
            SortKey.FILE_NAME, SortKey.FILE_PATH -> "nameSortKey"
        }
        return " ORDER BY $column ${direction(sort)}, id ASC"
    }

    private fun direction(sort: SortSpec): String =
        if (sort.direction == SortDirection.DESCENDING) "DESC" else "ASC"
}
