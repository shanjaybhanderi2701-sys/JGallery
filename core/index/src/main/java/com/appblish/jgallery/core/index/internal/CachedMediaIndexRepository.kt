package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.di.IndexSyncScope
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cached, incrementally-updated [MediaIndexRepository] (spec §1 rule 4).
 *
 * Reads are served straight from the persistent [MediaIndexStore] (Room), so opening the app renders
 * the last-known library instantly — no blocking full re-scan. On first observation it kicks off a
 * background sync and then keeps the cache live by re-syncing on every MediaStore change signal from
 * [StorageAccess.observeMediaChanges]. All work is off the main thread; filtering/sorting for a query
 * runs on [io].
 */
@Singleton
internal class CachedMediaIndexRepository @Inject constructor(
    private val store: MediaIndexStore,
    private val synchronizer: MediaIndexSynchronizer,
    private val storage: StorageAccess,
    @IndexSyncScope private val syncScope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MediaIndexRepository {

    private val syncing = AtomicBoolean(false)

    override fun observeAlbums(): Flow<List<Album>> =
        store.observeAlbums().onStart { ensureSyncing() }

    override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> =
        store.observeMedia()
            .map { items -> items.applyQuery(query) }
            .flowOn(io)
            .onStart { ensureSyncing() }

    /** Force a full reconcile (pull-to-refresh). Incremental sync is automatic otherwise. */
    override suspend fun refresh() {
        synchronizer.sync()
    }

    /**
     * Start the background sync loop exactly once (on the first collector). The initial [sync] does
     * the one-time full enumeration if the cache is empty; thereafter each MediaStore change triggers
     * an incremental sync. Failures are swallowed per-iteration so a transient error can't kill the
     * observer subscription — the next change signal retries.
     */
    private fun ensureSyncing() {
        if (!syncing.compareAndSet(false, true)) return
        syncScope.launch {
            runCatching { synchronizer.sync() }
            storage.observeMediaChanges().collect {
                runCatching { synchronizer.sync() }
            }
        }
    }

    private fun List<MediaItem>.applyQuery(query: MediaQuery): List<MediaItem> {
        val filtered = filter { item ->
            (query.bucketId == null || item.bucketId == query.bucketId) && item.type in query.types
        }
        return filtered.sortedWith(query.sort.comparator())
    }

    private fun SortSpec.comparator(): Comparator<MediaItem> {
        val ascending: Comparator<MediaItem> = when (key) {
            SortKey.FILE_NAME -> compareBy { it.displayName.lowercase() }
            SortKey.FILE_SIZE -> compareBy { it.sizeBytes }
            SortKey.LAST_MODIFIED -> compareBy { it.dateModifiedMillis }
            // MediaItem carries no path yet (added with Wave-2 file operations); fall back to name so
            // the sort is stable and total rather than throwing.
            SortKey.FILE_PATH -> compareBy { it.displayName.lowercase() }
        }
        return if (direction == SortDirection.DESCENDING) ascending.reversed() else ascending
    }
}
