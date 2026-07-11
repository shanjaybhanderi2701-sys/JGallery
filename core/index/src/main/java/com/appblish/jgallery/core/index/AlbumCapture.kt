package com.appblish.jgallery.core.index

import android.net.Uri
import com.appblish.jgallery.core.model.OperationResult

/**
 * The feature-facing handle for a "capture straight into album" (APP-424). Owned by `:core:index` so a
 * feature never names a `:core:storage` type: `:core:index → :core:storage` is an `implementation`
 * dependency, so a `:core:storage` `PendingCapture` returned across the boundary would not compile from
 * `feature:*`. [MediaOperationsRepository.beginCapture] returns this; the internal implementation wraps
 * the storage-owned pending capture and re-exposes only boundary-safe types (`Uri`, [OperationResult]).
 *
 * [outputUri] is passed to the system camera's `EXTRA_OUTPUT`; exactly one of [commit] (on `RESULT_OK`,
 * publishes) / [abort] (on cancel, discards) runs. An interface (not a concrete class) so JVM tests can
 * fake it without constructing an `android.net.Uri`.
 */
interface AlbumCapture {
    /** The `content://` destination to hand the system camera as `EXTRA_OUTPUT`. */
    val outputUri: Uri

    /** Publish the captured item on `RESULT_OK` — the album materialises holding its cover. */
    suspend fun commit(): OperationResult

    /** Discard the pending capture on cancel / failure — no orphan album or row is left. */
    suspend fun abort()
}
