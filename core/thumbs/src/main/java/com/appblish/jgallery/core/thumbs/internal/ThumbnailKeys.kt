package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.thumbs.FullImageRequest
import com.appblish.jgallery.core.thumbs.ThumbnailRequest

/**
 * Cache keys. The version (index `dateModifiedMillis`) is baked into every key, so invalidation is
 * structural: a changed file gets a new key and the stale entry simply ages out of the LRU — there
 * is no eviction call to forget.
 */
internal fun thumbnailMemoryKey(request: ThumbnailRequest): String =
    "thumb:${request.id.value}:v${request.versionMillis}"

internal fun thumbnailDiskKey(request: ThumbnailRequest, edgePx: Int): String =
    "thumb:${request.id.value}:v${request.versionMillis}:e$edgePx"

internal fun fullImageMemoryKey(request: FullImageRequest): String =
    "full:${request.id.value}:v${request.versionMillis}"
