package com.appblish.jgallery.core.playback.internal

import java.io.EOFException
import java.io.InputStream

/**
 * Pure-JVM byte-window math for [StorageAccessDataSource], kept free of Android/Media3 types so the
 * open/read/seek accounting is unit-testable on the JVM.
 */
internal object StreamMath {

    /** Mirrors Media3's `C.LENGTH_UNSET` without dragging its class into pure-JVM tests. */
    const val LENGTH_UNSET = -1L

    /**
     * Bytes left to serve for a window opened at [position]: an explicit spec [length] wins,
     * otherwise it is derived from the index's [sizeBytes] (0 = unknown → unset).
     */
    fun bytesRemaining(position: Long, length: Long, sizeBytes: Long): Long = when {
        length != LENGTH_UNSET -> length
        sizeBytes > 0 -> (sizeBytes - position).coerceAtLeast(0)
        else -> LENGTH_UNSET
    }

    /** How many bytes a read may pull without overrunning the window. */
    fun clampReadLength(requested: Int, bytesRemaining: Long): Int =
        if (bytesRemaining == LENGTH_UNSET) requested
        else minOf(requested.toLong(), bytesRemaining).toInt()

    /** Window accounting after a successful read. */
    fun afterRead(bytesRemaining: Long, read: Int): Long =
        if (bytesRemaining == LENGTH_UNSET) LENGTH_UNSET else bytesRemaining - read
}

/**
 * Advance the stream by exactly [count] bytes. `InputStream.skip` may legally make no progress, so
 * stalls fall back to draining reads; hitting EOF short of [count] throws (the extractor asked to
 * seek past the end of the media).
 */
internal fun InputStream.skipFully(count: Long) {
    require(count >= 0) { "Cannot skip $count bytes" }
    var remaining = count
    var scratch: ByteArray? = null
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
            continue
        }
        val buffer = scratch ?: ByteArray(DRAIN_BUFFER_BYTES).also { scratch = it }
        val read = read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
        if (read == -1) throw EOFException("Stream ended $remaining bytes before the requested position")
        remaining -= read
    }
}

private const val DRAIN_BUFFER_BYTES = 8 * 1024
