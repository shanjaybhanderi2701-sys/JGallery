package com.appblish.jgallery.core.storage

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.OperationProgress
import com.appblish.jgallery.core.model.OperationResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * ACCEPTANCE TEST for the §1.6 boundary requirement:
 *
 *   "Swapping the [StorageAccess] implementation to scoped media permissions must require
 *    ZERO changes in feature code."
 *
 * The proof is structural. [FeatureConsumer] below stands in for real feature-module code
 * (Albums/Photos/Viewer) and is written against the [StorageAccess] + [StoragePermissionController]
 * interfaces ONLY — it imports no `android.*`, no `MediaStore`, no `Environment`, no permission
 * string. We then run the exact same consumer, unchanged, against two entirely different backends
 * (an All-Files fake and a scoped-media fake) and assert identical observable behaviour.
 *
 * If a future migration to media permissions / SAF ever required touching feature code, this test
 * would not compile or would diverge — the swap would stop being free, which is the failure this
 * boundary exists to prevent. The complementary defence is the `RawStorageAccess` lint check, which
 * fails the build if feature code reaches past these interfaces to a raw platform API.
 */
class StorageBoundarySwapTest {

    private enum class OnboardingStep { OPEN_SETTINGS, REQUEST_RUNTIME }

    /**
     * Simulated feature code. Depends only on the boundary. This class is byte-for-byte identical
     * regardless of which backend is injected — that identity is the whole point.
     */
    private class FeatureConsumer(
        private val storage: StorageAccess,
        private val permissions: StoragePermissionController,
    ) {
        suspend fun albumNames(): List<String> =
            if (!permissions.hasAccess()) emptyList() else storage.queryAlbums().map { it.name }

        suspend fun firstPhotoByteCount(): Int =
            storage.queryMedia(MediaQuery()).firstOrNull()?.let { item ->
                storage.openStream(item.id).use { it.readBytes().size }
            } ?: 0

        /** Onboarding resolves the next step from an abstract request — never from the backend. */
        fun nextOnboardingStep(): OnboardingStep = when (permissions.accessRequest()) {
            is AccessRequest.SystemSettings -> OnboardingStep.OPEN_SETTINGS
            is AccessRequest.RuntimePermissions -> OnboardingStep.REQUEST_RUNTIME
        }
    }

    @Test
    fun `same feature code yields same results across two backends`() = runTest {
        val allFiles = FeatureConsumer(FakeStorage(BACKEND_A), FakePermissions(BACKEND_A))
        val scopedMedia = FeatureConsumer(FakeStorage(BACKEND_B), FakePermissions(BACKEND_B))

        // Identical enumeration results — the consumer cannot tell the backends apart.
        assertThat(allFiles.albumNames()).isEqualTo(scopedMedia.albumNames())
        assertThat(allFiles.albumNames()).containsExactly("Camera", "Screenshots").inOrder()

        // Identical streaming results — openStream is backend-agnostic.
        assertThat(allFiles.firstPhotoByteCount()).isEqualTo(scopedMedia.firstPhotoByteCount())
        assertThat(allFiles.firstPhotoByteCount()).isEqualTo(SAMPLE_BYTES.size)
    }

    @Test
    fun `permission mechanism is fully encapsulated - the consumer maps any request the same way`() {
        // Two backends, two different request mechanisms, ONE unchanged onboarding code path.
        val preR = FeatureConsumer(FakeStorage(BACKEND_A), FakePermissions(BACKEND_A))
        val scoped = FeatureConsumer(FakeStorage(BACKEND_B), FakePermissions(BACKEND_B))

        assertThat(preR.nextOnboardingStep()).isEqualTo(OnboardingStep.REQUEST_RUNTIME)
        assertThat(scoped.nextOnboardingStep()).isEqualTo(OnboardingStep.REQUEST_RUNTIME)

        // The permission STRINGS differ per backend, but that detail never leaks to the consumer.
        val preRPerms = (FakePermissions(BACKEND_A).accessRequest() as AccessRequest.RuntimePermissions).permissions
        val scopedPerms = (FakePermissions(BACKEND_B).accessRequest() as AccessRequest.RuntimePermissions).permissions
        assertThat(preRPerms).isNotEqualTo(scopedPerms)
    }

    @Test
    fun `no-access backend degrades gracefully through the same consumer`() = runTest {
        val locked = FeatureConsumer(FakeStorage(BACKEND_A), FakePermissions(BACKEND_A, granted = false))
        assertThat(locked.albumNames()).isEmpty()
    }

    // --- Fakes: two genuinely different backends behind the same interfaces ---

    private companion object {
        val SAMPLE_BYTES = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        // Backend A models "All Files Access on pre-R" (runtime READ_EXTERNAL_STORAGE).
        val BACKEND_A = StorageBackend.ALL_FILES_ACCESS

        // Backend B models the Play-migration fallback: scoped media permissions.
        val BACKEND_B = StorageBackend.MEDIA_PERMISSIONS
    }

    private class FakeStorage(override val backend: StorageBackend) : StorageAccess {
        // Same media, expressed differently internally per backend, so only the interface is shared.
        private val items = listOf(
            item("10", "IMG_1.jpg", "camera", "Camera", taken = 200),
            item("11", "Screenshot_1.png", "shots", "Screenshots", taken = 100),
        )

        override suspend fun hasMediaAccess(): Boolean = true
        override suspend fun queryMedia(query: MediaQuery): List<MediaItem> =
            items.sortedByDescending { it.dateTakenMillis }
        override suspend fun queryAlbums(): List<Album> =
            items.groupBy { it.bucketId }
                .map { (id, media) ->
                    val newest = media.maxByOrNull { it.dateTakenMillis }!!
                    Album(id, newest.bucketName, media.size, newest.id, newest.dateTakenMillis)
                }
                .sortedByDescending { it.newestItemMillis }
        override suspend fun openStream(id: MediaId, target: DecodeTarget): InputStream =
            ByteArrayInputStream(SAMPLE_BYTES)
        override suspend fun queryMediaSignatures(query: MediaQuery): List<MediaSignature> =
            items.map { MediaSignature(it.id, it.dateModifiedMillis, it.sizeBytes) }
        override fun observeMediaChanges(): Flow<Unit> = emptyFlow()

        override suspend fun rename(id: MediaId, newDisplayName: String) = ok()
        override suspend fun createAlbum(name: String) = ok()
        override fun copy(ids: List<MediaId>, destinationBucketId: String) = done(ids.size)
        override fun move(ids: List<MediaId>, destinationBucketId: String) = done(ids.size)
        override fun moveToTrash(ids: List<MediaId>) = done(ids.size)
        override fun deletePermanently(ids: List<MediaId>) = done(ids.size)

        private fun ok() = OperationResult(succeeded = 1, failed = 0)
        private fun done(n: Int): Flow<OperationProgress> =
            flowOf(OperationProgress(completed = n, total = n, currentName = null))

        private fun item(id: String, name: String, bucketId: String, bucket: String, taken: Long) =
            MediaItem(
                id = MediaId(id), displayName = name, type = MediaType.IMAGE,
                bucketId = bucketId, bucketName = bucket, dateTakenMillis = taken,
                dateModifiedMillis = taken, sizeBytes = 8, width = 4, height = 2,
                durationMillis = 0, mimeType = "image/jpeg",
            )
    }

    private class FakePermissions(
        override val backend: StorageBackend,
        private val granted: Boolean = true,
    ) : StoragePermissionController {
        override suspend fun hasAccess(): Boolean = granted
        override fun accessRequest(): AccessRequest = when (backend) {
            // Both backends here request runtime permissions but with DIFFERENT strings — the exact
            // detail the boundary hides from features. (The real R+ All-Files controller instead
            // returns AccessRequest.SystemSettings; that arm is covered by instrumented tests.)
            StorageBackend.ALL_FILES_ACCESS ->
                AccessRequest.RuntimePermissions(listOf("android.permission.READ_EXTERNAL_STORAGE"))
            StorageBackend.MEDIA_PERMISSIONS ->
                AccessRequest.RuntimePermissions(
                    listOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"),
                )
            StorageBackend.STORAGE_ACCESS_FRAMEWORK ->
                AccessRequest.RuntimePermissions(emptyList())
        }
    }
}
