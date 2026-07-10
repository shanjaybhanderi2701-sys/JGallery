package com.appblish.jgallery.core.thumbs

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.gif.AnimatedImageDecoder
import coil3.svg.SvgDecoder
import com.appblish.jgallery.core.thumbs.internal.RawImageDecoder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Wave 3 §8 format matrix, exercised against the SAME decoder component set the app registers
 * (see `ThumbnailModule`). The contract this proves: a supported format decodes to a bitmap, and an
 * undecodable/corrupt file resolves to a graceful [ErrorResult] — it NEVER throws or crashes, so the
 * viewer/tile can fall through to E15's placeholder.
 *
 * HEIC/WEBP/RAW/panorama are format-*routing* concerns validated by the JVM `MediaFormatTest` +
 * `RawMagicTest`; decoding them end-to-end needs real camera assets and is covered by the QA
 * real-device format pass. Here we fabricate the formats whose bytes we can synthesise reliably.
 */
@RunWith(AndroidJUnit4::class)
class FormatMatrixDecodeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val loader: ImageLoader = ImageLoader.Builder(context)
        .components {
            add(RawImageDecoder.Factory())
            add(SvgDecoder.Factory())
            add(AnimatedImageDecoder.Factory())
        }
        .build()

    // Bytes go in as a ByteBuffer (Coil's built-in fetcher) — no java.io.File, so the §1.6 storage
    // boundary lint stays satisfied even in the test. The registered decoders do the real work.
    private fun decode(bytes: ByteArray): coil3.request.ImageResult {
        val request = ImageRequest.Builder(context)
            .data(ByteBuffer.wrap(bytes))
            .size(64, 64)
            .allowHardware(false)
            .build()
        return runBlocking { loader.execute(request) }
    }

    @Test
    fun png_baseline_still_decodes_with_the_new_decoders_present() {
        assertThat(decode(PNG_1x1)).isInstanceOf(SuccessResult::class.java)
    }

    @Test
    fun animated_gif_decodes() {
        assertThat(decode(GIF_1x1)).isInstanceOf(SuccessResult::class.java)
    }

    @Test
    fun svg_decodes_best_effort() {
        assertThat(decode(SVG_BYTES)).isInstanceOf(SuccessResult::class.java)
    }

    @Test
    fun bmp_decodes() {
        assertThat(decode(bmp1x1())).isInstanceOf(SuccessResult::class.java)
    }

    @Test
    fun corrupt_bytes_fall_through_gracefully_without_crashing() {
        // Truncated/garbage data: the whole point of §8 — resolve to an error state, never throw.
        assertThat(decode(ByteArray(48) { it.toByte() })).isInstanceOf(ErrorResult::class.java)
    }

    private companion object {
        // 1×1 transparent PNG / GIF89a — smallest valid encodings, decoded via Base64 at runtime.
        val PNG_1x1: ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMCAQGZ8p0GAAAAAElFTkSuQmCC",
            Base64.DEFAULT,
        )
        val GIF_1x1: ByteArray = Base64.decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
            Base64.DEFAULT,
        )
        val SVG_BYTES: ByteArray =
            """<svg xmlns="http://www.w3.org/2000/svg" width="8" height="8"><rect width="8" height="8" fill="#2D6FF7"/></svg>"""
                .toByteArray()

        /** A minimal, valid 1×1 24-bit BMP built by hand (BITMAPINFOHEADER, bottom-up). */
        fun bmp1x1(): ByteArray {
            val pixelData = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00) // one BGR pixel + row padding
            val headerSize = 14 + 40
            val fileSize = headerSize + pixelData.size
            val buf = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)
            buf.put('B'.code.toByte()).put('M'.code.toByte())
            buf.putInt(fileSize).putInt(0).putInt(headerSize)
            buf.putInt(40).putInt(1).putInt(1)          // DIB size, width, height
            buf.putShort(1).putShort(24)                // planes, bpp
            buf.putInt(0).putInt(pixelData.size)        // compression=BI_RGB, image size
            buf.putInt(2835).putInt(2835).putInt(0).putInt(0) // ppm x/y, palette colors, important
            buf.put(pixelData)
            return buf.array()
        }
    }
}
