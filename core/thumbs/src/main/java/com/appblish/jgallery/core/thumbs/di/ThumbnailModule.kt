package com.appblish.jgallery.core.thumbs.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okio.Path.Companion.toOkioPath

/**
 * The mandated cached thumbnail pipeline (spec §1 rule 2): an in-memory LRU + an on-disk downsized
 * cache, all decode/IO off the main thread. Grid tiles must never trigger a full-size decode.
 *
 * This module owns the cache configuration. The custom fetcher/keyer that routes decode sources
 * through `:core:storage` (so no raw file access leaks in, and thumbnails are size-capped) is added
 * in APP-273; the caches and thresholds are set here so the whole app shares one tuned loader.
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
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    // In-memory LRU sized to ~25% of available app memory — the hot thumbnail set.
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("thumbnails").toOkioPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            // Memory + disk cache policies default to ENABLED in Coil 3.
            .components { add(VideoFrameDecoder.Factory()) } // video thumbnails from a decoded frame
            .crossfade(CROSSFADE_MS) // thumbnails fade in progressively (spec §1 rule 3)
            .build()

    private const val DISK_CACHE_BYTES = 256L * 1024 * 1024 // 256 MB on-disk thumbnail cache
    private const val CROSSFADE_MS = 120
}
