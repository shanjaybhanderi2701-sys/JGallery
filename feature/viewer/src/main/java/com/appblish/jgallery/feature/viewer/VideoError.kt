package com.appblish.jgallery.feature.viewer

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException

/**
 * Why a video page dropped out of playback (spec §8, design W3-05). Every case degrades gracefully:
 * the viewer keeps the poster on screen and overlays a card — it never crashes on an undecodable
 * clip. Reached only on open; we don't probe-decode every video while indexing (design W3-05).
 */
internal sealed interface VideoError {
    /**
     * The container demuxed fine but this device has no decoder for the codec (HEVC 10-bit, AV1,
     * VP9…). The headline W3-05 state: red card + codec chip + "Open with". [codecLabel] is a human
     * string ("HEVC", "AV1") or null when Media3 didn't report the offending format.
     */
    data class Unsupported(val codecLabel: String?) : VideoError

    /** Any other playback failure (I/O, malformed, source). Still graceful — a neutral "can't play". */
    data object Playback : VideoError
}

/**
 * Map a Media3 error code + the offending sample mime to a [VideoError]. Pure (no Media3 objects), so
 * the branch logic and codec labelling unit-test without a device.
 */
internal fun videoErrorFor(errorCode: Int, sampleMimeType: String?): VideoError =
    if (errorCode in UNSUPPORTED_CODEC_ERRORS) {
        VideoError.Unsupported(codecLabelForMime(sampleMimeType))
    } else {
        VideoError.Playback
    }

/** Bridge from the live Media3 exception to our state, pulling the renderer format when present. */
@OptIn(UnstableApi::class)
internal fun PlaybackException.toVideoError(): VideoError =
    videoErrorFor(errorCode, unsupportedSampleMime())

/** The mime of the format the renderer choked on — only [ExoPlaybackException] carries it. */
@OptIn(UnstableApi::class)
private fun PlaybackException.unsupportedSampleMime(): String? =
    (this as? ExoPlaybackException)
        ?.takeIf { it.type == ExoPlaybackException.TYPE_RENDERER }
        ?.rendererFormat
        ?.sampleMimeType

/** Media3 codes that mean "the device can't decode this", as opposed to an I/O or source failure. */
private val UNSUPPORTED_CODEC_ERRORS = setOf(
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
)

/**
 * Human codec name for the W3-05 chip. Covers the codecs a modern phone is most likely to *lack*
 * (HEVC / AV1 / VP9 / Dolby Vision) plus the common ones, and falls back to the raw subtype so the
 * chip is never blank when we do have a mime.
 */
internal fun codecLabelForMime(sampleMimeType: String?): String? {
    val mime = sampleMimeType?.lowercase() ?: return null
    return when (mime) {
        "video/hevc", "video/x-hevc" -> "HEVC"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/avc" -> "H.264"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "video/mpeg2" -> "MPEG-2"
        "video/dolby-vision" -> "Dolby Vision"
        else -> mime.substringAfter('/').uppercase()
    }
}
