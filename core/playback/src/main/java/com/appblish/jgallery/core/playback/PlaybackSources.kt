package com.appblish.jgallery.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import com.appblish.jgallery.core.model.MediaItem

/**
 * Media3 playback sources for the full-screen viewer (spec §5), routed through the §1.6 boundary.
 *
 * Features hand the returned [MediaSource] to an `ExoPlayer` — they never see a file path or
 * `content://` uri; the bytes stream through `StorageAccess.openStream` on Media3's loading thread.
 * This module is to video what `:core:thumbs` is to images: the sanctioned adapter between a media
 * engine and the storage boundary.
 */
@OptIn(UnstableApi::class) // MediaSource is Media3 @UnstableApi; this is our stable facade over it
interface PlaybackSources {

    /**
     * A progressive [MediaSource] for [item]'s original bytes. [MediaItem.sizeBytes] (from the
     * cached index) is reported to the extractor so seeking works even for MP4s whose index atom
     * sits at the end of the file.
     */
    fun mediaSource(item: MediaItem): MediaSource
}
