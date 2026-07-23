package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.MediaId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaDeltaTest {

    private fun sig(id: String, modified: Long = 1L, size: Long = 10L, name: String = "photo.jpg") =
        IndexSignature(MediaId(id), dateModifiedMillis = modified, sizeBytes = size, displayName = name)

    @Test
    fun `first sync treats every current row as changed`() {
        val delta = computeIndexDelta(persisted = emptyList(), current = listOf(sig("1"), sig("2")))

        assertThat(delta.changedIds).containsExactly(MediaId("1"), MediaId("2"))
        assertThat(delta.deletedIds).isEmpty()
    }

    @Test
    fun `unchanged rows are neither re-read nor deleted`() {
        val rows = listOf(sig("1"), sig("2"), sig("3"))

        val delta = computeIndexDelta(persisted = rows, current = rows)

        assertThat(delta.isEmpty).isTrue()
    }

    @Test
    fun `a changed date-modified or size marks a row changed`() {
        val persisted = listOf(sig("1", modified = 1L, size = 10L), sig("2", modified = 5L, size = 20L))
        val current = listOf(sig("1", modified = 2L, size = 10L), sig("2", modified = 5L, size = 99L))

        val delta = computeIndexDelta(persisted, current)

        assertThat(delta.changedIds).containsExactly(MediaId("1"), MediaId("2"))
        assertThat(delta.deletedIds).isEmpty()
    }

    @Test
    fun `a pure rename (same date and size, new name) marks the row changed`() {
        // APP-590 regression: a MediaStore DISPLAY_NAME write bumps neither DATE_MODIFIED nor SIZE, so
        // without the name in the fingerprint the incremental sync would drop the rename entirely and
        // the cache would keep the stale name forever.
        val persisted = listOf(sig("1", modified = 7L, size = 42L, name = "IMG_0001.jpg"))
        val current = listOf(sig("1", modified = 7L, size = 42L, name = "Sunset.jpg"))

        val delta = computeIndexDelta(persisted, current)

        assertThat(delta.changedIds).containsExactly(MediaId("1"))
        assertThat(delta.deletedIds).isEmpty()
    }

    @Test
    fun `rows gone from the device are deleted`() {
        val persisted = listOf(sig("1"), sig("2"), sig("3"))
        val current = listOf(sig("1"))

        val delta = computeIndexDelta(persisted, current)

        assertThat(delta.changedIds).isEmpty()
        assertThat(delta.deletedIds).containsExactly(MediaId("2"), MediaId("3"))
    }

    @Test
    fun `add, change and delete are reconciled in a single pass`() {
        val persisted = listOf(sig("1", modified = 1L), sig("2", modified = 1L), sig("3", modified = 1L))
        // 1 unchanged, 2 modified, 3 deleted, 4 added.
        val current = listOf(sig("1", modified = 1L), sig("2", modified = 2L), sig("4", modified = 1L))

        val delta = computeIndexDelta(persisted, current)

        assertThat(delta.changedIds).containsExactly(MediaId("2"), MediaId("4"))
        assertThat(delta.deletedIds).containsExactly(MediaId("3"))
    }
}
