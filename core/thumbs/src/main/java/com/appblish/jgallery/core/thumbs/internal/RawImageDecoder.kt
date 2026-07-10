package com.appblish.jgallery.core.thumbs.internal

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension

/**
 * Best-effort RAW preview (spec §8 / design W3-04). We never full-decode a RAW file — instead we
 * extract the **embedded JPEG preview** the camera already wrote into the file via [ExifInterface]
 * (fast, header-only), then downsample that JPEG to the requested size. RAW that carries no embedded
 * preview yields `null`, letting Coil fall through to the next decoder and ultimately to E15's
 * unsupported/corrupt placeholder — it never crashes (spec §8).
 *
 * This decoder is registered ahead of Coil's platform decoder, so it wins for TIFF-based RAW even on
 * devices whose `ImageDecoder` could technically decode a DNG: the embedded JPEG is the intended
 * best-effort path (no multi-hundred-MB RAW bitmap on the heap).
 */
internal class RawImageDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        // ExifInterface reads only the header/IFDs it needs from the stream — not the whole RAW —
        // so a 50 MB RAW never lands in memory. Ownership of the underlying source stays with Coil.
        val exif = runCatching {
            source.source().peek().inputStream().use { ExifInterface(it) }
        }.getOrNull() ?: return null

        if (!exif.hasThumbnail()) return null
        val jpeg = exif.thumbnailBytes ?: return null

        // Downsample the embedded JPEG to the requested target — never a full-size bitmap for a tile.
        val targetPx = options.size.maxDimensionPx()
        val opts = BitmapFactory.Options()
        if (targetPx > 0) {
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
            opts.inSampleSize = computeInSampleSize(opts.outWidth, opts.outHeight, targetPx)
            opts.inJustDecodeBounds = false
        }
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return null

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = opts.inSampleSize > 1,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
            if (isTiffBasedRaw(result.source)) RawImageDecoder(result.source, options) else null
    }
}

/**
 * Peek the first bytes (without consuming the source Coil hands to the next decoder) and match the
 * TIFF endianness marker that virtually every consumer RAW format (DNG/CR2/NEF/ARW/PEF/SRW/RW2…) is
 * built on. Plain JPEG/PNG/WEBP/HEIF never match, so they flow to the platform decoder untouched.
 */
private fun isTiffBasedRaw(source: ImageSource): Boolean = runCatching {
    isTiffRawMagic(source.source().peek().readByteArray(4L))
}.getOrDefault(false)

/**
 * Pure header test for a TIFF-based RAW file, factored out so it is JVM-unit-testable. [head] is the
 * first bytes of the file. Little-endian TIFF is "II" + 0x2A (or 0x55 for Panasonic RW2); big-endian
 * is "MM" + 0x00 0x2A. JPEG (FF D8), PNG (89 50), GIF (47 49) and RIFF/WEBP (52 49) never match.
 */
internal fun isTiffRawMagic(head: ByteArray): Boolean {
    if (head.size < 4) return false
    val b0 = head[0].toInt() and 0xFF
    val b1 = head[1].toInt() and 0xFF
    val b2 = head[2].toInt() and 0xFF
    val b3 = head[3].toInt() and 0xFF
    val littleEndian = b0 == 0x49 && b1 == 0x49 && (b2 == 0x2A || b2 == 0x55)
    val bigEndian = b0 == 0x4D && b1 == 0x4D && b2 == 0x00 && b3 == 0x2A
    return littleEndian || bigEndian
}

/** Largest concrete pixel dimension Coil asked for, or 0 when the size is unbounded/original. */
private fun coil3.size.Size.maxDimensionPx(): Int {
    val w = (width as? Dimension.Pixels)?.px ?: 0
    val h = (height as? Dimension.Pixels)?.px ?: 0
    return maxOf(w, h)
}

/** Standard power-of-two sub-sampling so the decoded edge stays ≥ the requested target. */
private fun computeInSampleSize(width: Int, height: Int, targetPx: Int): Int {
    var sample = 1
    var longest = maxOf(width, height)
    while (longest / 2 >= targetPx) {
        longest /= 2
        sample *= 2
    }
    return sample
}
