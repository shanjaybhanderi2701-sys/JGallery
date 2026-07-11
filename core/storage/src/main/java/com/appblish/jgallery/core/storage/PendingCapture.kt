package com.appblish.jgallery.core.storage

import android.net.Uri
import com.appblish.jgallery.core.model.OperationResult

/**
 * A handle to a still-pending capture destination inside a named album folder (APP-424). Minted by
 * [StorageAccess.beginCapture], its [outputUri] is handed to the *system camera* as an
 * `ACTION_IMAGE_CAPTURE` / `ACTION_VIDEO_CAPTURE` `EXTRA_OUTPUT` — the camera app (not JGallery) writes
 * the bytes, which is why JGallery declares no `CAMERA` permission (delegated capture; Security gate
 * APP-426). The row is invisible (`IS_PENDING`) until exactly one of [commit] / [abort] runs:
 *
 * - [commit] on `RESULT_OK` — publishes the file (clears `IS_PENDING`) *after* verifying the camera
 *   actually wrote bytes, so a cancelled-but-OK camera never publishes an empty item.
 * - [abort] on cancel / failure — deletes the pending row so no orphan album or row is left behind.
 *
 * Distinct from the internal `Sink` (which the *app* writes to via an `OutputStream`): here the writer
 * is the external camera, so the contract is uri-out + verify, and the method names mirror `Sink`'s
 * `commit()`/`abort()` rather than a stream lifecycle.
 */
interface PendingCapture {
    /** The `content://` destination to pass as the camera intent's `EXTRA_OUTPUT`. */
    val outputUri: Uri

    /** Publish the captured file after verifying bytes were written. One-item [OperationResult]. */
    suspend fun commit(): OperationResult

    /** Discard the pending row (cancel / failure). Idempotent-safe; leaves no orphan. */
    suspend fun abort()
}
