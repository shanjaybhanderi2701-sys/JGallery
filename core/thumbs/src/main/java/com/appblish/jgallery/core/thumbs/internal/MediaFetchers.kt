package com.appblish.jgallery.core.thumbs.internal

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Size
import com.appblish.jgallery.core.storage.DecodeTarget
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.ThumbnailBitmapSource
import com.appblish.jgallery.core.thumbs.FullImageRequest
import com.appblish.jgallery.core.thumbs.ThumbnailRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.Buffer
import okio.buffer
import okio.source
import java.io.ByteArrayOutputStream

/**
 * Coil adapter for [ThumbnailRequest] and the heart of the APP-391 R1 decode-once pipeline. Runs on
 * Coil's bounded fetcher dispatcher (IO) — decode/IO never touches the main thread (spec §1 rule 3).
 *
 * Cold grid tile, in priority order:
 *  1. **Disk hit** → hand the cached JPEG bytes back as a [SourceFetchResult]; Coil decodes ONCE.
 *  2. **Miss** → decode MediaStore's own downsized thumbnail exactly ONCE via [ThumbnailBitmapSource]
 *     and return the [Bitmap] directly as an [ImageFetchResult] — Coil does NOT re-decode. The
 *     JPEG re-encode + disk write-through happens off this continuation on [writeScope] (Fix 5's
 *     bounded scope), so the fling never waits on an encode.
 *  3. **Un-thumbnailable** (null bitmap) → stream the full-size original; Coil subsample-decodes once.
 *
 * That is one hot-path decode per tile, replacing the old decode → JPEG re-encode → re-decode triple.
 */
internal class ThumbnailFetcher(
    private val request: ThumbnailRequest,
    private val options: Options,
    private val pipeline: ThumbnailPipeline,
    private val thumbnailSource: ThumbnailBitmapSource,
    private val storage: StorageAccess,
    private val writeScope: CoroutineScope,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val requestedEdgePx = options.size.maxEdgePxOrNull()
            // Unbounded size = a caller that wants the original (viewer-style); the thumbnail
            // cache must not serve a downsized tile for it. Stream the source directly.
            ?: return fullStreamResult()

        // 1. Disk hit — one decode of the cached JPEG.
        pipeline.cached(request, requestedEdgePx)?.let { bytes ->
            return SourceFetchResult(
                source = ImageSource(
                    source = Buffer().apply { write(bytes) },
                    fileSystem = options.fileSystem,
                ),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        }

        // 2. Miss — decode MediaStore's downsized thumbnail ONCE at the bucketed edge.
        val edgePx = selectThumbnailBucket(requestedEdgePx)
        val bitmap = thumbnailSource.loadThumbnailBitmap(request.id, edgePx)
        if (bitmap != null) {
            // Off-path, bounded write-through: re-encode to JPEG and persist for next cold start. NOT
            // on the fetch continuation, so return latency is unaffected. The bitmap is shared with
            // Coil (shareable = true) — do NOT recycle; `compress` reads pixels, which is safe to run
            // concurrently with Coil's draw.
            writeScope.launch {
                runCatching { pipeline.writeThrough(request, requestedEdgePx, bitmap.toJpegBytes()) }
            }
            return ImageFetchResult(
                image = bitmap.asImage(shareable = true),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        }

        // 3. Graceful fallback for a source MediaStore cannot thumbnail — stream the original, still a
        // single (subsampled) Coil decode. Nothing is written through, so nothing is cached.
        return fullStreamResult()
    }

    private suspend fun fullStreamResult(): SourceFetchResult =
        SourceFetchResult(
            source = ImageSource(
                source = storage.openStream(request.id, DecodeTarget.Full).source().buffer(),
                fileSystem = options.fileSystem,
            ),
            mimeType = null,
            dataSource = DataSource.DISK,
        )

    class Factory(
        private val storage: StorageAccess,
        private val thumbnailSource: ThumbnailBitmapSource,
        cache: ThumbnailByteCache,
        private val writeScope: CoroutineScope,
    ) : Fetcher.Factory<ThumbnailRequest> {

        private val pipeline = ThumbnailPipeline(cache)

        override fun create(data: ThumbnailRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(data, options, pipeline, thumbnailSource, storage, writeScope)
    }
}

/** Re-encode a decoded thumbnail for the disk cache. Off the hot path — see [ThumbnailFetcher.fetch]. */
private fun Bitmap.toJpegBytes(quality: Int = THUMBNAIL_JPEG_QUALITY): ByteArray =
    ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

private const val THUMBNAIL_JPEG_QUALITY = 85

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
