package com.appblish.jgallery.core.thumbs.di

import android.app.ActivityManager
import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.gif.AnimatedImageDecoder
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.ThumbnailBitmapSource
import com.appblish.jgallery.core.thumbs.internal.CoilDiskThumbnailCache
import com.appblish.jgallery.core.thumbs.internal.DecodePoolSizing
import com.appblish.jgallery.core.thumbs.internal.FullImageFetcher
import com.appblish.jgallery.core.thumbs.internal.FullImageKeyer
import com.appblish.jgallery.core.thumbs.internal.RawImageDecoder
import com.appblish.jgallery.core.thumbs.internal.ThumbnailFetcher
import com.appblish.jgallery.core.thumbs.internal.ThumbnailKeyer
import com.appblish.jgallery.core.thumbs.internal.WriteBackGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import okio.Path.Companion.toOkioPath

/**
 * The mandated cached thumbnail pipeline (spec §1 rule 2): an in-memory LRU + an on-disk downsized
 * cache, all decode/IO off the main thread. Grid tiles must never trigger a full-size decode.
 *
 * The fetchers/keyers registered here route every decode source through `:core:storage`
 * ([ThumbnailRequest]/[FullImageRequest] are the only models features load), thumbnail sources are
 * size-capped at the boundary, and cache keys carry the index's last-modified version so stale
 * entries miss structurally (see `internal/ThumbnailPipeline`).
 *
 * Coil 3 core deliberately ships with NO network engine (the `coil-network-*` artifacts are never
 * added): the loader is structurally incapable of HTTP, which the APP-289 egress guard depends on —
 * adding a network artifact trips the guard and voids the §9.3 trust claim.
 */
@Module
@InstallIn(SingletonComponent::class)
object ThumbnailModule {

    @Provides
    @Singleton
    fun provideThumbnailDiskCache(@ApplicationContext context: Context): DiskCache =
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("thumbnails").toOkioPath())
            .maxSizeBytes(DISK_CACHE_BYTES)
            .build()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        diskCache: DiskCache,
        storage: StorageAccess,
        thumbnailSource: ThumbnailBitmapSource,
    ): ImageLoader {
        // Fix 5 — bounded decode concurrency: a fling must not flood IO with parallel decodes. Cap
        // Coil's fetch/decode parallelism so decodes queue instead of thrashing. The cap is
        // device-tier aware (APP-701): small-heap / low-RAM devices get the minimum so concurrent
        // decodes don't spike heap and trigger GC thrash mid-fling, while a big-heap, many-core
        // device unlocks more slots to cut decode latency. A separate, smaller bounded scope backs
        // the Fix-1c off-path JPEG write-through so encodes can't pile up either. Both draw from
        // Dispatchers.IO's shared thread pool (no new threads).
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice ?: false
        val memoryClassMb = activityManager?.memoryClass ?: DecodePoolSizing.LOW_HEAP_MB
        val decodeParallelism = DecodePoolSizing.parallelism(
            cores = Runtime.getRuntime().availableProcessors(),
            isLowRam = isLowRam,
            memoryClassMb = memoryClassMb,
        )
        // APP-709: split the pools. The FETCHER runs the IO-bound cold-thumbnail path
        // (`loadThumbnail`) — a small, downsized bitmap that blocks on MediaStore, not CPU/heap — so
        // it gets a WIDER, memory-tiered pool to fill just-landed visible tiles fast at 50k. The
        // DECODER keeps the narrow bound: its heap-heavy full-res subsample-decodes are the actual
        // fling-jank risk (APP-700/701 guardrail). The fling gate still suspends *lookahead* prefetch
        // above the velocity threshold, so a wider fetcher never re-floods a hard fling.
        val fetchParallelism = DecodePoolSizing.fetchParallelism(
            isLowRam = isLowRam,
            memoryClassMb = memoryClassMb,
        )
        val fetchContext = Dispatchers.IO.limitedParallelism(fetchParallelism)
        val decodeContext = Dispatchers.IO.limitedParallelism(decodeParallelism)
        // APP-712 (P0 ceiling fix): bound the write-back path with a semaphore-backed gate. At most
        // WRITE_BACK_CAP jobs can simultaneously hold a bitmap reference; surplus write-throughs are
        // dropped rather than queued, keeping heap pinning O(constant) no matter how far the user flings.
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(WRITE_PARALLELISM))
        val writeBackGate = WriteBackGate(writeScope, maxInFlight = WRITE_BACK_CAP)
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    // In-memory LRU sized to ~25% of available app memory — the hot thumbnail set.
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache(diskCache)
            .fetcherCoroutineContext(fetchContext)
            .decoderCoroutineContext(decodeContext)
            // Memory + disk cache policies default to ENABLED in Coil 3.
            .components {
                add(ThumbnailKeyer())
                add(FullImageKeyer())
                add(ThumbnailFetcher.Factory(storage, thumbnailSource, CoilDiskThumbnailCache(diskCache), writeBackGate))
                add(FullImageFetcher.Factory(storage))
                // Format breadth (W3-E13 §8). Each decoder content-sniffs and DECLINES sources it does
                // not own, so ordinary JPEG/PNG/HEIF/WEBP/BMP flow untouched to the platform decoder;
                // an undecodable file yields no bitmap and falls through to E15's placeholder (never a
                // crash). Grid GIF tiles stay static — the thumbnail fetcher serves a JPEG first frame,
                // so only the viewer's full-image stream reaches AnimatedImageDecoder and animates.
                add(RawImageDecoder.Factory()) // RAW → embedded JPEG (best-effort), before the platform
                add(SvgDecoder.Factory()) // SVG → rasterised vector (best-effort)
                add(AnimatedImageDecoder.Factory()) // animated GIF / WEBP / HEIF (API 28+)
                // Full-size video posters (viewer) decode a frame here. Grid video tiles never reach
                // this — the thumbnail fetcher already served downsized frame bytes from the boundary.
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(CROSSFADE_MS) // viewer thumbnails fade in progressively (grid opts out — Fix 3)
            .build()
    }

    private const val DISK_CACHE_BYTES = 256L * 1024 * 1024 // 256 MB on-disk thumbnail cache
    private const val CROSSFADE_MS = 120

    // A couple of IO threads drains the off-path JPEG write-through without competing with foreground
    // decodes. The gate cap is deliberately wider than WRITE_PARALLELISM: queued-but-not-yet-running
    // write-backs hold their bitmap reference, so WRITE_BACK_CAP is the true heap-pinning budget (not
    // just the running count). 8 ≈ < 8 MB pinned (8 × ~0.9 MB worst case) — safe on all device tiers.
    private const val WRITE_PARALLELISM = 2
    private const val WRITE_BACK_CAP = 8
}
