package com.appblish.jgallery.feature.albums

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.appblish.jgallery.core.index.AlbumCapture
import com.appblish.jgallery.core.index.FavoritesStore
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.CaptureKind
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Capture-straight-into-album orchestration (spec C1-09 item 9, APP-424). Verifies the ViewModel mints a
 * capture *by album name*, emits the handle for the screen to launch the camera, and commits/aborts on
 * the result — the JVM-testable half of delegated capture (the real MediaStore round-trip is C7). The
 * fake capture never exposes a real `android.net.Uri`, so no Robolectric is needed (per the seam review).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        bucketId: String = "camera",
        name: String = "Trip 2026",
        operations: FakeOperations = FakeOperations(),
        preferences: AlbumViewPreferences = FakeAlbumViewPreferences(),
    ) = AlbumDetailViewModel(
        SavedStateHandle(
            mapOf(
                ALBUM_DETAIL_BUCKET_ID_ARG to bucketId,
                ALBUM_DETAIL_NAME_ARG to name,
            ),
        ),
        FakeRepository(),
        operations,
        preferences,
        FakeFavoritesStore(),
    )

    @Test
    fun `requestCapture mints by album name and emits the handle to launch`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(name = "Trip 2026", operations = operations)
        val launched = mutableListOf<AlbumCapture>()
        val job = launch { vm.launchCapture.collect { launched += it } }
        advanceUntilIdle()

        vm.requestCapture(CaptureKind.PHOTO)
        advanceUntilIdle()

        assertThat(operations.beginCaptureCalls).containsExactly("Trip 2026" to CaptureKind.PHOTO)
        assertThat(launched).containsExactly(operations.lastCapture)
        job.cancel()
    }

    @Test
    fun `onCaptureResult success commits the pending capture`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(operations = operations)

        vm.requestCapture(CaptureKind.PHOTO)
        advanceUntilIdle()
        vm.onCaptureResult(success = true)
        advanceUntilIdle()

        assertThat(operations.lastCapture!!.committed).isTrue()
        assertThat(operations.lastCapture!!.aborted).isFalse()
    }

    @Test
    fun `onCaptureResult cancel aborts the pending capture, leaving no orphan`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(operations = operations)

        vm.requestCapture(CaptureKind.PHOTO)
        advanceUntilIdle()
        vm.onCaptureResult(success = false)
        advanceUntilIdle()

        assertThat(operations.lastCapture!!.aborted).isTrue()
        assertThat(operations.lastCapture!!.committed).isFalse()
    }

    @Test
    fun `onCaptureResult with no pending capture is a no-op`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(operations = operations)

        vm.onCaptureResult(success = true)
        advanceUntilIdle()

        assertThat(operations.lastCapture).isNull()
    }

    @Test
    fun `an invalid album name mints nothing and emits no launch`() = runTest(dispatcher) {
        val operations = FakeOperations().apply { mintCapture = false }
        val vm = viewModel(operations = operations)
        val launched = mutableListOf<AlbumCapture>()
        val job = launch { vm.launchCapture.collect { launched += it } }
        advanceUntilIdle()

        vm.requestCapture(CaptureKind.PHOTO)
        advanceUntilIdle()

        assertThat(operations.beginCaptureCalls).hasSize(1)
        assertThat(launched).isEmpty()
        job.cancel()
    }

    @Test
    fun `a smart album never captures`() = runTest(dispatcher) {
        val operations = FakeOperations()
        val vm = viewModel(bucketId = AlbumsCatalog.RECENT_BUCKET_ID, operations = operations)

        vm.requestCapture(CaptureKind.PHOTO)
        advanceUntilIdle()

        assertThat(operations.beginCaptureCalls).isEmpty()
    }

    @Test
    fun `a real folder sweeps orphaned captures on init, a smart album does not`() = runTest(dispatcher) {
        val real = FakeOperations()
        viewModel(bucketId = "camera", operations = real)
        advanceUntilIdle()
        assertThat(real.sweepCount).isEqualTo(1)

        val smart = FakeOperations()
        viewModel(bucketId = AlbumsCatalog.ALL_VIDEOS_BUCKET_ID, operations = smart)
        advanceUntilIdle()
        assertThat(smart.sweepCount).isEqualTo(0)
    }

    // --- Format filter carried in from the tapped album card (design C1-06, APP-467) --------------

    @Test
    fun `Videos filter yields only videos in album detail`() = runTest(dispatcher) {
        val vm = detailWithFilter(MediaFilter.VIDEOS, mixedMedia())
        val content = withTimeout(5_000) {
            vm.state.first { it is AlbumDetailUiState.Content } as AlbumDetailUiState.Content
        }
        assertThat(content.items.map { it.id.value }).containsExactly("vid")
    }

    @Test
    fun `Photos filter excludes videos and GIFs in album detail`() = runTest(dispatcher) {
        val vm = detailWithFilter(MediaFilter.PHOTOS, mixedMedia())
        val content = withTimeout(5_000) {
            vm.state.first { it is AlbumDetailUiState.Content } as AlbumDetailUiState.Content
        }
        assertThat(content.items.map { it.id.value }).containsExactly("jpg")
    }

    @Test
    fun `no filter arg defaults to ALL and shows everything`() = runTest(dispatcher) {
        val vm = detailWithFilter(filter = null, media = mixedMedia())
        val content = withTimeout(5_000) {
            vm.state.first { it is AlbumDetailUiState.Content } as AlbumDetailUiState.Content
        }
        assertThat(content.items.map { it.id.value }).containsExactly("vid", "jpg", "gif")
    }

    private fun detailWithFilter(filter: MediaFilter?, media: List<MediaItem>): AlbumDetailViewModel {
        val args = mutableMapOf<String, Any?>(
            ALBUM_DETAIL_BUCKET_ID_ARG to "camera",
            ALBUM_DETAIL_NAME_ARG to "Camera",
        )
        if (filter != null) args[ALBUM_DETAIL_FILTER_ARG] = filter.name
        return AlbumDetailViewModel(
            SavedStateHandle(args),
            MediaRepository(media),
            FakeOperations(),
            FakeAlbumViewPreferences(),
            FakeFavoritesStore(),
        )
    }

    // --- Favorites smart view (G2 · APP-543) ------------------------------------------------------

    @Test
    fun `favorites smart view queries the index restricted to the live starred ids`() =
        runTest(dispatcher) {
            val repo = RecordingRepository(emptyList())
            val store = FakeFavoritesStore()
            val vm = AlbumDetailViewModel(
                SavedStateHandle(
                    mapOf(
                        ALBUM_DETAIL_BUCKET_ID_ARG to AlbumsCatalog.FAVORITES_BUCKET_ID,
                        ALBUM_DETAIL_NAME_ARG to AlbumsCatalog.FAVORITES_NAME,
                    ),
                ),
                repo,
                FakeOperations(),
                FakeAlbumViewPreferences(),
                store,
            )
            store.setFavorite(MediaId("a"), true)
            store.setFavorite(MediaId("b"), true)
            val stateJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            // The index is queried with exactly the starred set — the real index resolves those rows.
            assertThat(repo.queries.last().ids).isEqualTo(setOf(MediaId("a"), MediaId("b")))

            // Un-starring one re-queries with the smaller set (live membership).
            store.setFavorite(MediaId("a"), false)
            advanceUntilIdle()
            assertThat(repo.queries.last().ids).isEqualTo(setOf(MediaId("b")))
            stateJob.cancel()
        }

    @Test
    fun `a real folder is never id-restricted`() = runTest(dispatcher) {
        val repo = RecordingRepository(mixedMedia())
        val vm = AlbumDetailViewModel(
            SavedStateHandle(mapOf(ALBUM_DETAIL_BUCKET_ID_ARG to "camera", ALBUM_DETAIL_NAME_ARG to "Camera")),
            repo,
            FakeOperations(),
            FakeAlbumViewPreferences(),
            FakeFavoritesStore(),
        )
        val stateJob = launch { vm.state.collect {} }
        advanceUntilIdle()
        assertThat(repo.queries.last().ids).isNull()
        assertThat(repo.queries.last().bucketId).isEqualTo("camera")
        stateJob.cancel()
    }

    // --- In-album Sort + Grid size + per-album scope (G1-9, APP-468) ------------------------------

    @Test
    fun `setSort persists into the album scope and re-queries the index with the new sort`() =
        runTest(dispatcher) {
            val repo = RecordingRepository(mixedMedia())
            val prefs = FakeAlbumViewPreferences()
            val vm = AlbumDetailViewModel(
                SavedStateHandle(mapOf(ALBUM_DETAIL_BUCKET_ID_ARG to "camera", ALBUM_DETAIL_NAME_ARG to "Camera")),
                repo,
                FakeOperations(),
                prefs,
                FakeFavoritesStore(),
            )
            val stateJob = launch { vm.state.collect {} }
            val settingsJob = launch { vm.viewSettings.collect {} }
            advanceUntilIdle()

            val byName = SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING)
            vm.setSort(byName)
            advanceUntilIdle()

            assertThat(repo.queries.map { it.sort }).contains(byName)
            assertThat(vm.viewSettings.value.sort).isEqualTo(byName)
            stateJob.cancel()
            settingsJob.cancel()
        }

    @Test
    fun `setColumns persists the grid density for the album`() = runTest(dispatcher) {
        val prefs = FakeAlbumViewPreferences()
        val vm = viewModel(preferences = prefs)
        val settingsJob = launch { vm.viewSettings.collect {} }
        advanceUntilIdle()

        vm.setColumns(ColumnCount(5))
        advanceUntilIdle()

        assertThat(vm.viewSettings.value.columns).isEqualTo(ColumnCount(5))
        settingsJob.cancel()
    }

    @Test
    fun `setGroupBy persists the time-sectioning for the album (APP-499 shared menu)`() =
        runTest(dispatcher) {
            val prefs = FakeAlbumViewPreferences()
            val vm = viewModel(preferences = prefs)
            val settingsJob = launch { vm.viewSettings.collect {} }
            advanceUntilIdle()

            // Default matches the Photos tab (DAY) so the shared Group-by menu opens on the same option.
            assertThat(vm.viewSettings.value.groupBy).isEqualTo(GroupBy.DAY)

            vm.setGroupBy(GroupBy.MONTH)
            advanceUntilIdle()

            assertThat(vm.viewSettings.value.groupBy).isEqualTo(GroupBy.MONTH)
            settingsJob.cancel()
        }

    @Test
    fun `setScope THIS_ALBUM then a change stays scoped to this album, not the global default`() =
        runTest(dispatcher) {
            val prefs = FakeAlbumViewPreferences()
            val vm = viewModel(bucketId = "camera", preferences = prefs)
            val settingsJob = launch { vm.viewSettings.collect {} }
            advanceUntilIdle()

            vm.setScope(ViewScope.THIS_ALBUM)
            advanceUntilIdle()
            assertThat(vm.viewSettings.value.scope).isEqualTo(ViewScope.THIS_ALBUM)

            vm.setColumns(ColumnCount(6))
            advanceUntilIdle()

            // This album shows 6…
            assertThat(vm.viewSettings.value.columns).isEqualTo(ColumnCount(6))
            // …but a sibling album still on ALL_ALBUMS keeps the untouched global default.
            assertThat(prefs.settings("screenshots").first().columns).isEqualTo(ColumnCount.DEFAULT)
            settingsJob.cancel()
        }

    private fun mixedMedia(): List<MediaItem> = listOf(
        mediaItem("vid", MediaType.VIDEO, "video/mp4"),
        mediaItem("jpg", MediaType.IMAGE, "image/jpeg"),
        mediaItem("gif", MediaType.IMAGE, "image/gif"),
    )

    private fun mediaItem(id: String, type: MediaType, mime: String) = MediaItem(
        id = MediaId(id),
        displayName = "$id.${mime.substringAfterLast('/')}",
        type = type,
        bucketId = "camera",
        bucketName = "Camera",
        dateTakenMillis = 0,
        dateModifiedMillis = 0,
        sizeBytes = 0,
        width = 100,
        height = 100,
        durationMillis = if (type == MediaType.VIDEO) 1000 else 0,
        mimeType = mime,
    )

    private class FakeRepository : MediaIndexRepository {
        override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())
        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> = MutableStateFlow(emptyList())
        override suspend fun refresh() = Unit
    }

    private class MediaRepository(private val media: List<MediaItem>) : MediaIndexRepository {
        override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())
        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> = MutableStateFlow(media)
        override suspend fun refresh() = Unit
    }

    /** Records every [MediaQuery] it is handed so a test can assert the persisted sort reached the index. */
    private class RecordingRepository(private val media: List<MediaItem>) : MediaIndexRepository {
        val queries = mutableListOf<MediaQuery>()
        override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())
        override fun observeMedia(query: MediaQuery): Flow<List<MediaItem>> {
            queries += query
            return MutableStateFlow(media)
        }
        override suspend fun refresh() = Unit
    }

    /**
     * In-memory [AlbumViewPreferences] mirroring the DataStore impl's scope routing: a shared global
     * default plus per-album overrides, resolved by each album's [ViewScope]. Lets the ViewModel wiring
     * be tested without a real DataStore; the DataStore semantics themselves are covered separately.
     */
    private class FakeAlbumViewPreferences : AlbumViewPreferences {
        private var global = AlbumViewSettings()
        private val perAlbumSort = mutableMapOf<String, SortSpec>()
        private val perAlbumColumns = mutableMapOf<String, ColumnCount>()
        private val perAlbumGroup = mutableMapOf<String, GroupBy>()
        private val scopes = mutableMapOf<String, ViewScope>()
        private val flows = mutableMapOf<String, MutableStateFlow<AlbumViewSettings>>()

        override fun settings(bucketId: String): Flow<AlbumViewSettings> =
            flows.getOrPut(bucketId) { MutableStateFlow(resolve(bucketId)) }

        override suspend fun setSort(bucketId: String, sort: SortSpec, scope: ViewScope) {
            scopes[bucketId] = scope
            if (scope == ViewScope.ALL_ALBUMS) global = global.copy(sort = sort) else perAlbumSort[bucketId] = sort
            emitAll()
        }

        override suspend fun setColumns(bucketId: String, columns: ColumnCount, scope: ViewScope) {
            scopes[bucketId] = scope
            if (scope == ViewScope.ALL_ALBUMS) global = global.copy(columns = columns) else perAlbumColumns[bucketId] = columns
            emitAll()
        }

        override suspend fun setGroupBy(bucketId: String, groupBy: GroupBy, scope: ViewScope) {
            scopes[bucketId] = scope
            if (scope == ViewScope.ALL_ALBUMS) global = global.copy(groupBy = groupBy) else perAlbumGroup[bucketId] = groupBy
            emitAll()
        }

        override suspend fun setScope(bucketId: String, scope: ViewScope) {
            scopes[bucketId] = scope
            emitAll()
        }

        private fun resolve(bucketId: String): AlbumViewSettings {
            val scope = scopes[bucketId] ?: ViewScope.ALL_ALBUMS
            return if (scope == ViewScope.ALL_ALBUMS) {
                global.copy(scope = scope)
            } else {
                AlbumViewSettings(
                    sort = perAlbumSort[bucketId] ?: global.sort,
                    columns = perAlbumColumns[bucketId] ?: global.columns,
                    groupBy = perAlbumGroup[bucketId] ?: global.groupBy,
                    scope = scope,
                )
            }
        }

        // A global change ripples to every ALL_ALBUMS album, so re-resolve all live flows.
        private fun emitAll() = flows.forEach { (bucketId, flow) -> flow.value = resolve(bucketId) }
    }

    private class FakeAlbumCapture : AlbumCapture {
        var committed = false
        var aborted = false
        // The screen reads this to launch the camera; the JVM test never does (no real Uri exists here).
        override val outputUri: Uri get() = error("outputUri is not read in the JVM capture test")
        override suspend fun commit(): OperationResult {
            committed = true
            return OperationResult(succeeded = 1, failed = 0)
        }
        override suspend fun abort() {
            aborted = true
        }
    }

    private class FakeOperations : MediaOperationsRepository {
        val beginCaptureCalls = mutableListOf<Pair<String, CaptureKind>>()
        var lastCapture: FakeAlbumCapture? = null
        var mintCapture = true
        var sweepCount = 0

        override suspend fun beginCapture(albumName: String, kind: CaptureKind): AlbumCapture? {
            beginCaptureCalls += albumName to kind
            return if (mintCapture) FakeAlbumCapture().also { lastCapture = it } else null
        }
        override suspend fun sweepOrphanedCaptures(): Int {
            sweepCount++
            return 0
        }

        override suspend fun createAlbum(name: String) = OperationResult(succeeded = 1, failed = 0)
        override suspend fun rename(id: MediaId, newDisplayName: String) = OperationResult(succeeded = 1, failed = 0)
        override suspend fun viewUri(id: MediaId): Uri? = null
        override fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun exportCopy(ids: List<MediaId>, treeUri: Uri): Flow<FileOperationEvent> = emptyFlow()
        override fun copyToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> = emptyFlow()
        override fun moveToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> = emptyFlow()
        override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
        override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> = emptyFlow()
        override suspend fun renameAlbum(bucketId: String, newName: String) = OperationResult(succeeded = 1, failed = 0)
        override fun copyAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun moveAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> = emptyFlow()
        override fun deleteAlbum(bucketId: String): Flow<FileOperationEvent> = emptyFlow()
    }

    private class FakeFavoritesStore : FavoritesStore {
        private val ids = MutableStateFlow<Set<MediaId>>(emptySet())
        override val favoriteIds: Flow<Set<MediaId>> = ids
        override fun isFavorite(id: MediaId): Flow<Boolean> = ids.map { id in it }
        override suspend fun setFavorite(id: MediaId, favorite: Boolean) {
            ids.value = if (favorite) ids.value + id else ids.value - id
        }
        override suspend fun toggle(id: MediaId) = setFavorite(id, id !in ids.value)
    }
}
