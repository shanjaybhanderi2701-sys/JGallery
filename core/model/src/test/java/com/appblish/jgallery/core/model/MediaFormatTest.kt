package com.appblish.jgallery.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaFormatTest {

    private fun image(
        name: String = "photo.jpg",
        mime: String = "image/jpeg",
        width: Int = 4000,
        height: Int = 3000,
        type: MediaType = MediaType.IMAGE,
    ) = MediaItem(
        id = MediaId("1"),
        displayName = name,
        type = type,
        bucketId = "b",
        bucketName = "Camera",
        dateTakenMillis = 0,
        dateModifiedMillis = 0,
        sizeBytes = 0,
        width = width,
        height = height,
        durationMillis = 0,
        mimeType = mime,
    )

    // --- classifyImageFormat: MIME wins ---

    @Test
    fun `classifies the format-breadth matrix by mime`() {
        assertThat(classifyImageFormat("image/jpeg")).isEqualTo(ImageFormat.STANDARD)
        assertThat(classifyImageFormat("image/png")).isEqualTo(ImageFormat.STANDARD)
        assertThat(classifyImageFormat("image/heic")).isEqualTo(ImageFormat.HEIF)
        assertThat(classifyImageFormat("image/heif")).isEqualTo(ImageFormat.HEIF)
        assertThat(classifyImageFormat("image/webp")).isEqualTo(ImageFormat.WEBP)
        assertThat(classifyImageFormat("image/bmp")).isEqualTo(ImageFormat.BMP)
        assertThat(classifyImageFormat("image/gif")).isEqualTo(ImageFormat.GIF)
        assertThat(classifyImageFormat("image/svg+xml")).isEqualTo(ImageFormat.SVG)
        assertThat(classifyImageFormat("image/x-adobe-dng")).isEqualTo(ImageFormat.RAW)
        assertThat(classifyImageFormat("image/x-sony-arw")).isEqualTo(ImageFormat.RAW)
    }

    @Test
    fun `mime match is case and whitespace insensitive`() {
        assertThat(classifyImageFormat("  IMAGE/GIF ")).isEqualTo(ImageFormat.GIF)
        assertThat(classifyImageFormat("Image/Svg+Xml")).isEqualTo(ImageFormat.SVG)
    }

    // --- extension fallback when MIME is generic/empty ---

    @Test
    fun `falls back to extension when mime is generic or empty`() {
        assertThat(classifyImageFormat("application/octet-stream", "photo.dng")).isEqualTo(ImageFormat.RAW)
        assertThat(classifyImageFormat("", "clip.gif")).isEqualTo(ImageFormat.GIF)
        assertThat(classifyImageFormat("application/octet-stream", "logo.svg")).isEqualTo(ImageFormat.SVG)
        assertThat(classifyImageFormat("", "shot.nef")).isEqualTo(ImageFormat.RAW)
        assertThat(classifyImageFormat("", "img.heic")).isEqualTo(ImageFormat.HEIF)
    }

    @Test
    fun `bare extension without a dot still classifies`() {
        assertThat(classifyImageFormat("", "cr2")).isEqualTo(ImageFormat.RAW)
    }

    @Test
    fun `unknown image mime with no useful extension is OTHER, not a crash`() {
        assertThat(classifyImageFormat("image/x-unheard-of")).isEqualTo(ImageFormat.OTHER)
        assertThat(classifyImageFormat("")).isEqualTo(ImageFormat.OTHER)
    }

    // --- MediaItem derivations ---

    @Test
    fun `imageFormat uses mime and extension together`() {
        assertThat(image(mime = "image/gif").imageFormat).isEqualTo(ImageFormat.GIF)
        assertThat(image(name = "raw.dng", mime = "application/octet-stream").imageFormat)
            .isEqualTo(ImageFormat.RAW)
    }

    @Test
    fun `videos are never classified as special image formats`() {
        val video = image(name = "clip.gif", mime = "image/gif", type = MediaType.VIDEO)
        assertThat(video.imageFormat).isEqualTo(ImageFormat.STANDARD)
        assertThat(video.isAnimatedImage).isFalse()
        assertThat(video.formatBadge).isNull()
    }

    @Test
    fun `only gif counts as an animated image`() {
        assertThat(image(mime = "image/gif").isAnimatedImage).isTrue()
        assertThat(image(mime = "image/webp").isAnimatedImage).isFalse()
        assertThat(image(mime = "image/jpeg").isAnimatedImage).isFalse()
    }

    // --- panorama ---

    @Test
    fun `wide aspect at or beyond 2 to 1 is a panorama, either orientation`() {
        assertThat(image(width = 8000, height = 3000).isPanorama).isTrue() // 2.67:1
        assertThat(image(width = 6000, height = 3000).isPanorama).isTrue() // exactly 2:1
        assertThat(image(width = 3000, height = 8000).isPanorama).isTrue() // tall pano
    }

    @Test
    fun `normal and unknown-dimension images are not panoramas`() {
        assertThat(image(width = 4000, height = 3000).isPanorama).isFalse()
        assertThat(image(width = 0, height = 0).isPanorama).isFalse()
    }

    // --- badge precedence ---

    @Test
    fun `format badge maps one-to-one and prefers format over pano`() {
        assertThat(image(mime = "image/gif").formatBadge).isEqualTo(FormatBadge.GIF)
        assertThat(image(mime = "image/svg+xml").formatBadge).isEqualTo(FormatBadge.SVG)
        assertThat(image(name = "r.dng", mime = "").formatBadge).isEqualTo(FormatBadge.RAW)
        // A wide GIF is still first a GIF, not a panorama.
        assertThat(image(mime = "image/gif", width = 8000, height = 2000).formatBadge)
            .isEqualTo(FormatBadge.GIF)
        // A wide plain JPEG is a panorama.
        assertThat(image(mime = "image/jpeg", width = 8000, height = 2000).formatBadge)
            .isEqualTo(FormatBadge.PANO)
        // An ordinary photo has no badge.
        assertThat(image().formatBadge).isNull()
    }

    // --- best-effort ---

    @Test
    fun `best-effort kind is set only for raw and svg`() {
        assertThat(image(name = "r.dng", mime = "").bestEffortKind).isEqualTo(BestEffortKind.RAW_EMBEDDED_JPEG)
        assertThat(image(mime = "image/svg+xml").bestEffortKind).isEqualTo(BestEffortKind.SVG_PREVIEW)
        assertThat(image(mime = "image/heic").bestEffortKind).isNull()
        assertThat(image(mime = "image/jpeg").bestEffortKind).isNull()
    }
}
