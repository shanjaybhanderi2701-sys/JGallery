package com.appblish.jgallery.benchmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import com.appblish.jgallery.feature.photos.PhotosScreen
import com.appblish.jgallery.feature.photos.PhotosUiState
import com.appblish.jgallery.feature.photos.buildPhotosTimeline
import java.time.LocalDate
import java.time.ZoneId

/**
 * Headline-gate benchmark surface (spec §11: the operator-assisted 10 000+ item frame-time pass,
 * APP-342). Exists ONLY in the `benchmark` build variant — see app/src/benchmark/AndroidManifest.xml
 * — so it can never reach a shipped debug/release APK.
 *
 * It renders the real, stateless [PhotosScreen] body over a fixed [FIXTURE_ITEM_COUNT]-item fixture,
 * with no Hilt / onboarding gate / MediaStore in the path. That is deliberate: the 10k gate is about
 * the Photos scroll hot path (timeline cells → `LazyVerticalGrid` reuse + tile layout), and this
 * isolates exactly that path so `FrameTimingMetric` measures composition/layout/draw cost rather than
 * disk IO or thumbnail decode variance. It mirrors how PhotosGridTest already drives the same
 * stateless overload with a synthetic fixture.
 *
 * The root sets [testTagsAsResourceId] so the out-of-process macrobenchmark can locate the
 * `photos_grid` list by resource-id via UiAutomator (`By.res(pkg, "photos_grid")`).
 */
@OptIn(ExperimentalComposeUiApi::class) // testTagsAsResourceId — the supported UiAutomator seam
class PhotosBenchmarkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val timeline = buildPhotosTimeline(
            items = fixtureItems(FIXTURE_ITEM_COUNT),
            zone = ZoneId.systemDefault(),
            today = LocalDate.now(ZoneId.systemDefault()),
        )
        setContent {
            JGalleryTheme {
                var columns by remember { mutableStateOf(ColumnCount.DEFAULT) }
                PhotosScreen(
                    state = PhotosUiState.Content(timeline),
                    columns = columns,
                    onColumnsChange = { columns = it },
                    modifier = Modifier
                        .fillMaxSize()
                        // Expose Compose testTags as View resource-ids for UiAutomator.
                        .semantics { testTagsAsResourceId = true },
                )
            }
        }
    }

    private companion object {
        /** Comfortably clears the ">= 15,000 items" perf-spec bar (APP-369) with date-header cells on top. */
        const val FIXTURE_ITEM_COUNT = 15_500

        /**
         * Synthetic media spread across many capture days so the timeline builds realistic
         * date-header sections (headers are full-width span cells the scroll must also lay out).
         * The ids/paths intentionally resolve to nothing — thumbnails miss and draw the placeholder,
         * which is the right signal: we are timing the list/tile machinery, not IO.
         */
        fun fixtureItems(count: Int): List<MediaItem> {
            val dayMillis = 24L * 60 * 60 * 1_000
            // Anchor off a fixed epoch so the fixture is deterministic run-to-run.
            val base = 1_700_000_000_000L // 2023-11-14T22:13:20Z
            return List(count) { i ->
                val takenAt = base - (i / 30L) * dayMillis - (i % 30L) * 60_000L
                val video = i % 11 == 0
                MediaItem(
                    id = MediaId("bench-$i"),
                    displayName = "IMG_%05d".format(i),
                    type = if (video) MediaType.VIDEO else MediaType.IMAGE,
                    bucketId = "bench-bucket-${i % 8}",
                    bucketName = "Camera ${i % 8}",
                    dateTakenMillis = takenAt,
                    dateModifiedMillis = takenAt,
                    sizeBytes = 2_500_000L,
                    width = 4032,
                    height = 3024,
                    durationMillis = if (video) 15_000L else 0L,
                    mimeType = if (video) "video/mp4" else "image/jpeg",
                )
            }
        }
    }
}
