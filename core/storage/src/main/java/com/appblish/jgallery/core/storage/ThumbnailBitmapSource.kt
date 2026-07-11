package com.appblish.jgallery.core.storage

import android.graphics.Bitmap
import com.appblish.jgallery.core.model.MediaId

/**
 * A narrow §1.6 SPI, deliberately kept **alongside** [StorageAccess] rather than folded into it: only
 * `:core:thumbs` consumes it, and widening the main boundary with an Android `Bitmap` method would
 * force every JVM `StorageAccess` fake (~6 of them) to implement a platform type they never use.
 *
 * It exists for one reason — the APP-391 R1 decode-once fix. The old thumbnail path decoded
 * MediaStore's own downsized thumbnail, re-encoded it to JPEG, then let Coil decode that JPEG again:
 * three heavy pixel ops per cold tile. Handing back the already-decoded [Bitmap] here lets the
 * fetcher return it to Coil directly (one decode) and move the JPEG re-encode+cache write off the
 * fling-critical path.
 *
 * The boundary invariant is unchanged: `:core:storage` stays the only module touching
 * MediaStore/`ContentResolver`. A `Bitmap` may cross (this module is an Android library and already
 * decodes internally); no MediaStore/`ContentResolver`/`Environment` type leaks past it.
 */
interface ThumbnailBitmapSource {

    /**
     * MediaStore's own downsized thumbnail for [id], decoded exactly ONCE and bounded to [maxEdgePx].
     * Runs off the caller's thread (on `Dispatchers.IO`). Returns `null` when the provider cannot
     * thumbnail the source (corrupt / unsupported / genuinely un-downsizable), which is the fetcher's
     * signal to stream the full-size original instead (a graceful, still single-decode fallback).
     */
    suspend fun loadThumbnailBitmap(id: MediaId, maxEdgePx: Int): Bitmap?
}
