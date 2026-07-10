package com.appblish.jgallery.feature.viewer

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure mapping logic behind the W3-05 codec-unsupported card (spec §8): which Media3 error codes mean
 * "this device can't decode it" vs. a generic playback failure, and how a sample mime becomes the
 * codec chip label. Kept device-free so the branch table is a fast unit gate.
 */
class VideoErrorTest {

    @Test
    fun `decoder-unsupported codes map to the Unsupported state with a codec label`() {
        val error = videoErrorFor(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            sampleMimeType = "video/hevc",
        )
        assertEquals(VideoError.Unsupported("HEVC"), error)
    }

    @Test
    fun `every decoder-family code is treated as codec-unsupported`() {
        val codes = listOf(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        )
        codes.forEach { code ->
            val error = videoErrorFor(code, sampleMimeType = "video/av01")
            assertEquals("code $code should be Unsupported", VideoError.Unsupported("AV1"), error)
        }
    }

    @Test
    fun `non-decoder failures fall back to the neutral Playback state`() {
        val error = videoErrorFor(
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            sampleMimeType = "video/avc",
        )
        assertEquals(VideoError.Playback, error)
    }

    @Test
    fun `unsupported with an unknown mime still yields a card, just without a label`() {
        val error = videoErrorFor(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            sampleMimeType = null,
        )
        assertEquals(VideoError.Unsupported(null), error)
    }

    @Test
    fun `codec labels humanise the mimes a phone is most likely to lack`() {
        assertEquals("HEVC", codecLabelForMime("video/hevc"))
        assertEquals("AV1", codecLabelForMime("video/av01"))
        assertEquals("VP9", codecLabelForMime("video/x-vnd.on2.vp9"))
        assertEquals("H.264", codecLabelForMime("video/avc"))
        assertEquals("Dolby Vision", codecLabelForMime("video/dolby-vision"))
        // Case-insensitive, and an unknown subtype degrades to the raw uppercased subtype.
        assertEquals("HEVC", codecLabelForMime("VIDEO/HEVC"))
        assertEquals("THEORA", codecLabelForMime("video/theora"))
        assertNull(codecLabelForMime(null))
    }
}
