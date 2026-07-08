package com.appblish.jgallery.core.thumbs.internal

import coil3.disk.DiskCache

/**
 * Byte-oriented port over the on-disk thumbnail cache, so the pipeline (and its JVM tests) never
 * touch Coil types. Thumbnail payloads are tile-sized (tens of KB), so whole-array round-trips are
 * deliberate — simpler than snapshot lifetimes and irrelevant to memory at this size.
 *
 * Callers run on Coil's fetcher dispatcher (IO); methods here may block on file IO.
 */
internal interface ThumbnailByteCache {
    fun read(key: String): ByteArray?
    fun write(key: String, bytes: ByteArray)
}

/** The real store: Coil's LRU [DiskCache] (size-bounded, configured in `ThumbnailModule`). */
internal class CoilDiskThumbnailCache(private val diskCache: DiskCache) : ThumbnailByteCache {

    override fun read(key: String): ByteArray? =
        diskCache.openSnapshot(key)?.use { snapshot ->
            diskCache.fileSystem.read(snapshot.data) { readByteArray() }
        }

    override fun write(key: String, bytes: ByteArray) {
        // null editor = another request is already writing this key; their result serves us next time.
        val editor = diskCache.openEditor(key) ?: return
        try {
            diskCache.fileSystem.write(editor.data) { write(bytes) }
            editor.commit()
        } catch (t: Throwable) {
            editor.abort()
            throw t
        }
    }
}
