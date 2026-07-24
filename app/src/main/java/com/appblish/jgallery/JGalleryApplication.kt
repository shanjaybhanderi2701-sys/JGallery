package com.appblish.jgallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.appblish.jgallery.crash.CrashlyticsMessageSanitizer
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

    override fun onCreate() {
        super.onCreate()
        // Chain a message sanitizer in FRONT of Crashlytics' auto-init uncaught-exception handler
        // (registered during super.onCreate() via its ContentProvider) so user file paths/names in
        // OS-generated crash messages are redacted before upload (APP-626 / APP-619 Finding 3). The
        // Crashlytics upload path stays intact — we only forward a sanitized throwable to it.
        CrashlyticsMessageSanitizer.install()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
