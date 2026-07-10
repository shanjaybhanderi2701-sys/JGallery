package com.appblish.jgallery.core.thumbs.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The RAW decoder only claims TIFF-based RAW; everything else must fall through to the platform
 * decoder untouched. This guards that routing gate without needing Android graphics.
 */
class RawMagicTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `matches little and big endian tiff plus panasonic rw2`() {
        assertThat(isTiffRawMagic(bytes(0x49, 0x49, 0x2A, 0x00))).isTrue() // DNG/CR2/NEF/ARW…
        assertThat(isTiffRawMagic(bytes(0x4D, 0x4D, 0x00, 0x2A))).isTrue() // big-endian TIFF RAW
        assertThat(isTiffRawMagic(bytes(0x49, 0x49, 0x55, 0x00))).isTrue() // Panasonic RW2
    }

    @Test
    fun `declines the ordinary formats the platform decoder owns`() {
        assertThat(isTiffRawMagic(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isFalse() // JPEG
        assertThat(isTiffRawMagic(bytes(0x89, 0x50, 0x4E, 0x47))).isFalse() // PNG
        assertThat(isTiffRawMagic(bytes(0x47, 0x49, 0x46, 0x38))).isFalse() // GIF
        assertThat(isTiffRawMagic(bytes(0x52, 0x49, 0x46, 0x46))).isFalse() // RIFF (WEBP)
        assertThat(isTiffRawMagic(bytes(0x00, 0x00, 0x00, 0x18))).isFalse() // HEIF ftyp box
    }

    @Test
    fun `too-short header is not raw`() {
        assertThat(isTiffRawMagic(bytes(0x49, 0x49))).isFalse()
        assertThat(isTiffRawMagic(ByteArray(0))).isFalse()
    }
}
