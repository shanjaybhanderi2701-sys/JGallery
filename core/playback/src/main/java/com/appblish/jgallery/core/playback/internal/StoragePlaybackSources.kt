package com.appblish.jgallery.core.playback.internal

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.playback.PlaybackSources
import com.appblish.jgallery.core.storage.StorageAccess
import javax.inject.Inject
import javax.inject.Singleton
import androidx.media3.common.MediaItem as Media3MediaItem

/**
 * The uri handed to Media3 is synthetic — [StorageAccessDataSource] is bound to the item at
 * construction and never resolves it. It only exists so player/media-session plumbing has a stable
 * identity per item.
 */
@Singleton
@OptIn(UnstableApi::class)
internal class StoragePlaybackSources @Inject constructor(
    private val storage: StorageAccess,
) : PlaybackSources {

    override fun mediaSource(item: MediaItem): MediaSource =
        ProgressiveMediaSource.Factory(StorageAccessDataSource.Factory(storage, item))
            .createMediaSource(Media3MediaItem.fromUri(syntheticUri(item)))

    private fun syntheticUri(item: MediaItem): Uri =
        Uri.Builder()
            .scheme("jgallery")
            .authority("media")
            .appendPath(item.id.value)
            .build()
}
