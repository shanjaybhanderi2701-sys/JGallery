package com.appblish.jgallery.feature.viewer

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure formatting behind the §5.1 Info dialog — timezone/locale-stable, no Android on the classpath. */
class MediaInfoTest {

    private val utc = ZoneId.of("UTC")

    private fun item(
        displayName: String = "IMG_0001.jpg",
        type: MediaType = MediaType.IMAGE,
        bucketName: String = "Camera",
        dateModifiedMillis: Long = 0L,
        sizeBytes: Long = 0L,
        width: Int = 0,
        height: Int = 0,
        mimeType: String = "image/jpeg",
    ) = MediaItem(
        id = MediaId("1"),
        displayName = displayName,
        type = type,
        bucketId = "b",
        bucketName = bucketName,
        dateTakenMillis = 0L,
        dateModifiedMillis = dateModifiedMillis,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMillis = 0L,
        mimeType = mimeType,
    )

    @Test
    fun `rows are the exact spec 5_1 set in order`() {
        val labels = mediaInfoRows(item(), zone = utc).map { it.label }
        assertEquals(listOf("Name", "Format", "Path", "Size", "Resolution", "Modified"), labels)
    }

    @Test
    fun `format prefers the mime subtype, uppercased`() {
        assertEquals("JPEG", formatFormat(item(mimeType = "image/jpeg")))
        assertEquals("MP4", formatFormat(item(mimeType = "video/mp4")))
    }

    @Test
    fun `format falls back to the file extension when the mime is empty`() {
        assertEquals("PNG", formatFormat(item(mimeType = "", displayName = "shot.PNG")))
    }

    @Test
    fun `format is unknown when nothing identifies the container`() {
        assertEquals("—", formatFormat(item(mimeType = "", displayName = "noext")))
    }

    @Test
    fun `size is base-1024 with one decimal above bytes`() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("1.5 KB", formatFileSize(1536))
        assertEquals("2.0 MB", formatFileSize(2L * 1024 * 1024))
        assertEquals("3.0 GB", formatFileSize(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun `size is unknown for zero or negative`() {
        assertEquals("—", formatFileSize(0))
        assertEquals("—", formatFileSize(-1))
    }

    @Test
    fun `resolution needs both dimensions`() {
        assertEquals("4032 × 3024", formatResolution(4032, 3024))
        assertEquals("—", formatResolution(0, 3024))
        assertEquals("—", formatResolution(4032, 0))
    }

    @Test
    fun `modified is unknown when the index has no timestamp`() {
        assertEquals("—", formatModified(0L, utc))
        assertEquals("—", formatModified(-5L, utc))
    }

    @Test
    fun `modified renders a real timestamp`() {
        // 2021-06-15T10:30:00Z — just assert it is non-empty and not the unknown sentinel, since the
        // exact rendering is locale-dependent (medium date / short time).
        val rendered = formatModified(1_623_753_000_000L, utc)
        assert(rendered != "—")
        assert(rendered.contains("2021"))
    }

    @Test
    fun `blank name and path degrade to unknown`() {
        val rows = mediaInfoRows(item(displayName = "", bucketName = ""), zone = utc).associate { it.label to it.value }
        assertEquals("—", rows["Name"])
        assertEquals("—", rows["Path"])
    }
}
