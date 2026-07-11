package com.appblish.jgallery.core.playback.internal

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.playback.PlaybackSources
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.playerkit.PlaybackSource
import com.appblish.playerkit.progressiveMediaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JGallery's **plain-file** [PlaybackSource] (APP-402): it plugs the boundary-routed
 * [StorageAccessDataSource] into the shared `:core:playerkit` seam, exactly where CalcVault plugs
 * its encrypted source. The uri handed to Media3 is synthetic — [StorageAccessDataSource] is bound
 * to the item at construction and never resolves it; it only exists so player/media-session
 * plumbing has a stable identity per item.
 */
@Singleton
@OptIn(UnstableApi::class)
internal class StoragePlaybackSources @Inject constructor(
    private val storage: StorageAccess,
) : PlaybackSources {

    override fun mediaSource(item: MediaItem): MediaSource =
        object : PlaybackSource {
            override val uri: Uri = syntheticUri(item)
            override fun dataSourceFactory(): DataSource.Factory =
                StorageAccessDataSource.Factory(storage, item)
        }.progressiveMediaSource()

    private fun syntheticUri(item: MediaItem): Uri =
        Uri.Builder()
            .scheme("jgallery")
            .authority("media")
            .appendPath(item.id.value)
            .build()
}
