package com.appblish.jgallery.core.index.internal

import android.net.Uri
import com.appblish.jgallery.core.index.AlbumCapture
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.storage.PendingCapture

/**
 * Wraps the §1.6 [PendingCapture] as the index-owned [AlbumCapture], keeping the `:core:storage` type
 * off every feature's classpath (`:core:storage` is an `implementation` dependency of `:core:index`).
 * Pure delegation — no behaviour of its own.
 */
internal class StorageAlbumCapture(
    private val pending: PendingCapture,
) : AlbumCapture {
    override val outputUri: Uri get() = pending.outputUri
    override suspend fun commit(): OperationResult = pending.commit()
    override suspend fun abort() = pending.abort()
}
