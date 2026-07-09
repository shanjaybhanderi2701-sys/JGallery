package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.TrashEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** JVM coverage for the bin-manifest (de)serialization — round-trip fidelity and corruption tolerance. */
class TrashRecordCodecTest {

    private fun entry(id: String, name: String, type: MediaType = MediaType.IMAGE) = TrashEntry(
        id = MediaId(id),
        displayName = name,
        type = type,
        mimeType = "image/jpeg",
        originalBucketId = "bucket-$id",
        originalBucketName = "Camera",
        originalRelativePath = "DCIM/Camera/",
        trashedAtMillis = 1_700_000_000_000L + id.hashCode(),
        sizeBytes = 123L,
        width = 4,
        height = 3,
        durationMillis = 0L,
    )

    @Test
    fun `encode then decode round-trips every field`() {
        val entries = listOf(entry("1", "a.jpg"), entry("2", "clip.mp4", MediaType.VIDEO))
        val decoded = TrashRecordCodec.decode(TrashRecordCodec.encode(entries))
        assertThat(decoded).isEqualTo(entries)
    }

    @Test
    fun `free-text fields survive tabs and newlines (base64-encoded, cannot break the record grid)`() {
        val nasty = entry("9", "we\tird\nname (1).jpg").copy(originalBucketName = "My\tAlbum\n2024")
        val decoded = TrashRecordCodec.decode(TrashRecordCodec.encode(listOf(nasty)))
        assertThat(decoded).containsExactly(nasty)
    }

    @Test
    fun `an empty manifest encodes and decodes to empty`() {
        assertThat(TrashRecordCodec.encode(emptyList())).isEmpty()
        assertThat(TrashRecordCodec.decode("")).isEmpty()
    }

    @Test
    fun `a single corrupt line is skipped, not fatal to the rest of the bin`() {
        val good = TrashRecordCodec.encode(listOf(entry("1", "a.jpg"), entry("2", "b.jpg")))
        val corrupted = "garbage-not-tab-separated\n$good\nalso\tbad\tline"
        val decoded = TrashRecordCodec.decode(corrupted)
        assertThat(decoded.map { it.id.value }).containsExactly("1", "2")
    }
}
