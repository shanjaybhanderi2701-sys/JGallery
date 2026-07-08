package com.appblish.jgallery.core.thumbs.internal

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Size
import com.appblish.jgallery.core.storage.DecodeTarget
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.thumbs.FullImageRequest
import com.appblish.jgallery.core.thumbs.ThumbnailRequest
import okio.Buffer
import okio.buffer
import okio.source

/**
 * Coil adapter for [ThumbnailRequest]: thin translation from Coil's requested size to the
 * [ThumbnailPipeline], which owns the cache discipline. Runs on Coil's fetcher dispatcher (IO) —
 * decode/IO never touches the main thread (spec §1 rule 3).
 */
internal class ThumbnailFetcher(
    private val request: ThumbnailRequest,
    private val options: Options,
    private val pipeline: ThumbnailPipeline,
    private val storage: StorageAccess,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val requestedEdgePx = options.size.maxEdgePxOrNull()
            // Unbounded size = a caller that wants the original (viewer-style); the thumbnail
            // cache must not serve a downsized tile for it. Stream the source directly.
            ?: return SourceFetchResult(
                source = ImageSource(
                    source = storage.openStream(request.id, DecodeTarget.Full).source().buffer(),
                    fileSystem = options.fileSystem,
                ),
                mimeType = null,
                dataSource = DataSource.DISK,
            )

        val result = pipeline.load(request, requestedEdgePx)
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(result.bytes) },
                fileSystem = options.fileSystem,
            ),
            mimeType = null, // let the decoder sniff — cache holds JPEG, the fallback path may not be
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val storage: StorageAccess, cache: ThumbnailByteCache) :
        Fetcher.Factory<ThumbnailRequest> {

        private val pipeline = ThumbnailPipeline(storage, cache)

        override fun create(data: ThumbnailRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(data, options, pipeline, storage)
    }
}

/**
 * Coil adapter for [FullImageRequest] (the E7 viewer): streams the original bytes through the §1.6
 * boundary. No thumbnail-cache involvement — Coil's own subsampled decode still bounds the bitmap
 * to the requested size, and its memory cache keeps the decoded result.
 */
internal class FullImageFetcher(
    private val request: FullImageRequest,
    private val options: Options,
    private val storage: StorageAccess,
) : Fetcher {

    override suspend fun fetch(): FetchResult =
        SourceFetchResult(
            source = ImageSource(
                source = storage.openStream(request.id, DecodeTarget.Full).source().buffer(),
                fileSystem = options.fileSystem,
            ),
            mimeType = null,
            dataSource = DataSource.DISK,
        )

    class Factory(private val storage: StorageAccess) : Fetcher.Factory<FullImageRequest> {
        override fun create(data: FullImageRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            FullImageFetcher(data, options, storage)
    }
}

/** Memory-cache keys. Coil size-checks cached values itself, so keys carry id + version only. */
internal class ThumbnailKeyer : Keyer<ThumbnailRequest> {
    override fun key(data: ThumbnailRequest, options: Options): String = thumbnailMemoryKey(data)
}

internal class FullImageKeyer : Keyer<FullImageRequest> {
    override fun key(data: FullImageRequest, options: Options): String = fullImageMemoryKey(data)
}

/** Largest concrete pixel edge Coil asked for, or null when the size is unbounded/original. */
private fun Size.maxEdgePxOrNull(): Int? {
    val w = (width as? Dimension.Pixels)?.px ?: 0
    val h = (height as? Dimension.Pixels)?.px ?: 0
    return maxOf(w, h).takeIf { it > 0 }
}
