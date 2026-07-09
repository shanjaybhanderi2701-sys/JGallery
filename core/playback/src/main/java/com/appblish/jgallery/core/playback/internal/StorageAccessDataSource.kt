package com.appblish.jgallery.core.playback.internal

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.storage.DecodeTarget
import com.appblish.jgallery.core.storage.StorageAccess
import java.io.InputStream
import kotlinx.coroutines.runBlocking

/**
 * Media3 [DataSource] that serves [item]'s bytes from `StorageAccess.openStream` — the §1.6
 * boundary stays the only place that resolves a [MediaItem] to real storage. Seeks reopen the
 * stream and skip forward; for local media that is a cheap `lseek`, and it keeps the boundary
 * surface free of any random-access/file-descriptor concept.
 *
 * Threading: Media3 calls [open]/[read] on its loading thread, never the main thread (spec §1
 * rule 3), so bridging the suspend boundary with [runBlocking] here is the Media3 contract, not a
 * main-thread block.
 */
@OptIn(UnstableApi::class)
internal class StorageAccessDataSource(
    private val storage: StorageAccess,
    private val item: MediaItem,
) : BaseDataSource(/* isNetwork = */ false) {

    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining = StreamMath.LENGTH_UNSET
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val source = runBlocking { storage.openStream(item.id, DecodeTarget.Full) }
        stream = source
        source.skipFully(dataSpec.position)
        bytesRemaining = StreamMath.bytesRemaining(dataSpec.position, dataSpec.length, item.sizeBytes)
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val toRead = StreamMath.clampReadLength(length, bytesRemaining)
        if (toRead == 0) return C.RESULT_END_OF_INPUT
        // Early EOF (index size stale vs the file on disk) degrades to end-of-input rather than an
        // error: an truncated tail plays as far as it can instead of crashing the viewer (spec §8).
        val read = stream?.read(buffer, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        if (read == -1) return C.RESULT_END_OF_INPUT
        bytesRemaining = StreamMath.afterRead(bytesRemaining, read)
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            stream?.close()
        } finally {
            stream = null
            bytesRemaining = StreamMath.LENGTH_UNSET
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    /** One factory per [MediaItem]: every open serves that item, whatever uri the spec carries. */
    class Factory(
        private val storage: StorageAccess,
        private val item: MediaItem,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = StorageAccessDataSource(storage, item)
    }
}
