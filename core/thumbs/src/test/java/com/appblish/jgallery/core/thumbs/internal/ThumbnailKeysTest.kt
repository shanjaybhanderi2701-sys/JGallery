package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.thumbs.FullImageRequest
import com.appblish.jgallery.core.thumbs.ThumbnailRequest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThumbnailKeysTest {

    private val request = ThumbnailRequest(MediaId("42"), versionMillis = 1_000L)

    @Test
    fun `keys are stable for the same id and version`() {
        assertThat(thumbnailMemoryKey(request)).isEqualTo(thumbnailMemoryKey(request.copy()))
        assertThat(thumbnailDiskKey(request, 256)).isEqualTo(thumbnailDiskKey(request.copy(), 256))
    }

    @Test
    fun `a version bump (file modified per the index) changes every key`() {
        val modified = request.copy(versionMillis = 2_000L)
        assertThat(thumbnailMemoryKey(modified)).isNotEqualTo(thumbnailMemoryKey(request))
        assertThat(thumbnailDiskKey(modified, 256)).isNotEqualTo(thumbnailDiskKey(request, 256))
    }

    @Test
    fun `disk keys separate size buckets, memory keys do not`() {
        assertThat(thumbnailDiskKey(request, 128)).isNotEqualTo(thumbnailDiskKey(request, 256))
    }

    @Test
    fun `thumbnail and full-image keys never collide for the same media`() {
        val full = FullImageRequest(MediaId("42"), versionMillis = 1_000L)
        assertThat(fullImageMemoryKey(full)).isNotEqualTo(thumbnailMemoryKey(request))
    }
}
