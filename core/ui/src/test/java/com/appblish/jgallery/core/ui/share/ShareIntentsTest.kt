package com.appblish.jgallery.core.ui.share

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The MIME-narrowing that tags the multi-select share intent (G2 · APP-541). Pure logic — the actual
 * [android.content.Intent] assembly in [ShareIntents.buildSendIntent] is device-verified, but the
 * narrowing decides which apps the chooser offers, so its every branch is locked here.
 */
class ShareIntentsTest {

    @Test
    fun `empty selection falls back to the universal type`() {
        assertThat(ShareIntents.commonMimeType(emptyList())).isEqualTo("*/*")
    }

    @Test
    fun `a homogeneous batch keeps its exact type`() {
        assertThat(ShareIntents.commonMimeType(listOf("image/jpeg", "image/jpeg")))
            .isEqualTo("image/jpeg")
    }

    @Test
    fun `mixed image subtypes collapse to the shared top-level`() {
        assertThat(ShareIntents.commonMimeType(listOf("image/jpeg", "image/png", "image/webp")))
            .isEqualTo("image/*")
    }

    @Test
    fun `all-video collapses to the video top-level`() {
        assertThat(ShareIntents.commonMimeType(listOf("video/mp4", "video/webm")))
            .isEqualTo("video/*")
    }

    @Test
    fun `a genuine image plus video mix widens to the universal type`() {
        assertThat(ShareIntents.commonMimeType(listOf("image/jpeg", "video/mp4")))
            .isEqualTo("*/*")
    }

    @Test
    fun `any unresolved type widens to the universal type so no target is filtered out`() {
        assertThat(ShareIntents.commonMimeType(listOf("image/jpeg", null))).isEqualTo("*/*")
        assertThat(ShareIntents.commonMimeType(listOf("image/jpeg", "  "))).isEqualTo("*/*")
    }

    @Test
    fun `a single item keeps its own exact type`() {
        assertThat(ShareIntents.commonMimeType(listOf("video/mp4"))).isEqualTo("video/mp4")
    }
}
