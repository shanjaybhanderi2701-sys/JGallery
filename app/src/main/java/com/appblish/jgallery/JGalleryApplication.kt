package com.appblish.jgallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

/**
 * Serves the Hilt-built loader from `:core:thumbs` as the app-wide singleton, so every
 * `AsyncImage` in every feature goes through the cached thumbnail pipeline (spec §1 rule 2) —
 * shared LRU, shared disk cache, boundary-routed fetchers. Provider-injected: the loader is built
 * lazily on first image request, not on app start.
 */
@HiltAndroidApp
class JGalleryApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: Provider<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
