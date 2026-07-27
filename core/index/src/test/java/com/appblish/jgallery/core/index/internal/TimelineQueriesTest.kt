package com.appblish.jgallery.core.index.internal

import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.TimelineSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the raw SQL the windowed timeline generates (APP-700). The window, ids and skeleton queries
 * MUST share one WHERE + ORDER (order + membership can never disagree, APP-704 D2/D5) and MUST bind
 * only genuine runtime data — bucketId, ids, limit, offset — while inlining the closed-enum fragments.
 */
class TimelineQueriesTest {

    /** Records what a [SupportSQLiteQuery] binds, so arg order + values are assertable off-device. */
    private class RecordingProgram : SupportSQLiteProgram {
        val args = ArrayList<Any?>()
        private fun set(index: Int, value: Any?) {
            while (args.size < index) args.add(null)
            args[index - 1] = value
        }
        override fun bindNull(index: Int) = set(index, null)
        override fun bindLong(index: Int, value: Long) = set(index, value)
        override fun bindDouble(index: Int, value: Double) = set(index, value)
        override fun bindString(index: Int, value: String) = set(index, value)
        override fun bindBlob(index: Int, value: ByteArray) = set(index, value)
        override fun clearBindings() = args.clear()
        override fun close() = Unit
    }

    private fun SupportSQLiteQuery.boundArgs(): List<Any?> =
        RecordingProgram().also { bindTo(it) }.args

    @Test
    fun `default window sorts by effectiveTime desc, id asc, with LIMIT OFFSET last`() {
        val q = TimelineQueries.window(TimelineSpec(), offset = 120, limit = 60)

        assertThat(q.sql).isEqualTo(
            "SELECT * FROM media WHERE type IN ('IMAGE','VIDEO') " +
                "ORDER BY effectiveTimeMillis DESC, id ASC LIMIT ? OFFSET ?",
        )
        assertThat(q.boundArgs()).containsExactly(60L, 120L).inOrder()
    }

    @Test
    fun `bucket, filter and ids all land in the WHERE in a stable arg order`() {
        val spec = TimelineSpec(
            bucketId = "cam",
            filter = MediaFilter.GIFS,
            ids = linkedSetOf(MediaId("x"), MediaId("y")),
        )

        val q = TimelineQueries.ids(spec)

        assertThat(q.sql).isEqualTo(
            "SELECT id FROM media WHERE type IN ('IMAGE','VIDEO') AND bucketId = ? " +
                "AND formatBucket = ? AND id IN (?,?) ORDER BY effectiveTimeMillis DESC, id ASC",
        )
        assertThat(q.boundArgs()).containsExactly("cam", "GIFS", "x", "y").inOrder()
    }

    @Test
    fun `an empty ids set compiles to a match-nothing predicate`() {
        val q = TimelineQueries.ids(TimelineSpec(ids = emptySet()))

        assertThat(q.sql).contains("AND 0")
        assertThat(q.boundArgs()).isEmpty()
    }

    @Test
    fun `name sort orders by nameSortKey ascending with id tiebreak`() {
        val spec = TimelineSpec(sort = SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING))

        val q = TimelineQueries.window(spec, offset = 0, limit = 10)

        assertThat(q.sql).contains("ORDER BY nameSortKey ASC, id ASC")
    }

    @Test
    fun `size sort orders by sizeBytes`() {
        val spec = TimelineSpec(sort = SortSpec(SortKey.FILE_SIZE, SortDirection.DESCENDING))
        assertThat(TimelineQueries.window(spec, 0, 10).sql).contains("ORDER BY sizeBytes DESC, id ASC")
    }

    @Test
    fun `day-count skeleton groups by localDayKey ordered by day`() {
        val q = TimelineQueries.dayCounts(TimelineSpec())

        assertThat(q.sql).isEqualTo(
            "SELECT localDayKey AS dayKey, COUNT(*) AS itemCount FROM media " +
                "WHERE type IN ('IMAGE','VIDEO') GROUP BY localDayKey ORDER BY localDayKey DESC",
        )
    }

    @Test
    fun `dayKeys projection reuses the same WHERE and ORDER as the window`() {
        val spec = TimelineSpec(sort = SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING), filter = MediaFilter.PHOTOS)

        val projection = TimelineQueries.dayKeys(spec)
        val window = TimelineQueries.window(spec, 0, 10)

        // Identical membership + order clause (skeleton and window can never disagree).
        val sharedOrder = "WHERE type IN ('IMAGE','VIDEO') AND formatBucket = ? ORDER BY nameSortKey ASC, id ASC"
        assertThat(projection.sql).endsWith(sharedOrder)
        assertThat(window.sql).contains(sharedOrder)
    }

    @Test
    fun `isTimeSort only for LAST_MODIFIED`() {
        assertThat(TimelineQueries.isTimeSort(SortSpec())).isTrue()
        assertThat(TimelineQueries.isTimeSort(SortSpec(SortKey.FILE_NAME))).isFalse()
        assertThat(TimelineQueries.isTimeSort(SortSpec(SortKey.FILE_SIZE))).isFalse()
    }

    @Test
    fun `types restriction narrows the type IN set`() {
        val spec = TimelineSpec(types = setOf(MediaType.VIDEO))
        assertThat(TimelineQueries.window(spec, 0, 10).sql).contains("type IN ('VIDEO')")
    }
}
