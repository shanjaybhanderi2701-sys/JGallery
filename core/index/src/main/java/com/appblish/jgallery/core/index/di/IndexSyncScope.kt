package com.appblish.jgallery.core.index.di

import javax.inject.Qualifier

/**
 * Application-lifetime [kotlinx.coroutines.CoroutineScope] the index uses to run its background sync
 * loop, so the work outlives any single screen's collection. Qualified so it can't be confused with
 * a UI/viewmodel scope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IndexSyncScope
