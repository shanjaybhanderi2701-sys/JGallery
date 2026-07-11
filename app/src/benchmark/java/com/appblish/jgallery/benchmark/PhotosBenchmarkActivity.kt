package com.appblish.jgallery.benchmark

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import com.appblish.jgallery.feature.photos.PhotosScreen
import com.appblish.jgallery.feature.photos.PhotosUiState
import com.appblish.jgallery.feature.photos.buildPhotosTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Headline-gate benchmark surface (spec §11: the operator-assisted 10 000+ item frame-time pass).
 * Exists ONLY in the `benchmark` build variant — see app/src/benchmark/AndroidManifest.xml — so it
 * can never reach a shipped debug/release APK.
 *
 * ## What changed for APP-390 (R0-1)
 * It used to render synthetic [MediaItem]s whose ids resolved to nothing, so every tile MISSED the
 * thumbnail fetcher and drew a placeholder — the benchmark measured list/layout only and gave a
 * false ~1.9% jank pass. It now:
 *   1. Seeds a corpus of ≥10,000 REAL image files into MediaStore ([BenchmarkCorpusSeeder]).
 *   2. Renders the real, stateless [PhotosScreen] over tiles carrying REAL MediaStore row-ids, so a
 *      fling drives the true `loadThumbnail` → decode → Coil path (the app's actual scroll hot path).
 *
 * Seeding runs OFF the main thread ([Dispatchers.IO]); the grid (`photos_grid` tag) only appears
 * once the corpus is ready, so the macrobenchmark's grid-ready wait doubles as a seed barrier.
 * Seeding is idempotent, so only the first (unmeasured, setup-time) iteration pays for it.
 *
 * The root sets [testTagsAsResourceId] so the out-of-process macrobenchmark can locate the
 * `photos_grid` list by resource-id via UiAutomator.
 */
@OptIn(ExperimentalComposeUiApi::class) // testTagsAsResourceId — the supported UiAutomator seam
class PhotosBenchmarkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val corpusSize = intent.getIntExtra(EXTRA_CORPUS_SIZE, BenchmarkCorpusSeeder.DEFAULT_CORPUS_SIZE)
        val appContext = applicationContext

        setContent {
            JGalleryTheme {
                var columns by remember { mutableStateOf(ColumnCount.DEFAULT) }
                // Seed + query on IO, then build the timeline. Loading until the corpus is ready.
                val state by produceState<PhotosUiState>(initialValue = PhotosUiState.Loading, corpusSize) {
                    val items = withContext(Dispatchers.IO) {
                        val seeded = BenchmarkCorpusSeeder.seed(appContext, corpusSize)
                        logDecodeSelfCheck(appContext, seeded)
                        seeded
                    }
                    val zone = ZoneId.systemDefault()
                    value = if (items.isEmpty()) {
                        PhotosUiState.Empty
                    } else {
                        PhotosUiState.Content(
                            buildPhotosTimeline(items = items, zone = zone, today = LocalDate.now(zone)),
                        )
                    }
                }

                when (val s = state) {
                    is PhotosUiState.Content -> PhotosScreen(
                        state = s,
                        columns = columns,
                        onColumnsChange = { columns = it },
                        modifier = Modifier
                            .fillMaxSize()
                            // Expose Compose testTags as View resource-ids for UiAutomator.
                            .semantics { testTagsAsResourceId = true },
                    )
                    // Before the corpus is ready (or if seeding produced nothing) show a tagged
                    // placeholder — deliberately NOT `photos_grid`, so the macrobenchmark keeps
                    // waiting through the one-time seed rather than measuring an empty screen.
                    else -> Box(
                        Modifier
                            .fillMaxSize()
                            .semantics {
                                testTagsAsResourceId = true
                                testTag = "bench_seeding"
                            },
                    )
                }
            }
        }
    }

    private companion object {
        /**
         * Optional intent extra to override the corpus size (`am start … --ei corpus_size N`), so a
         * CI smoke run can seed fewer files while the authoritative FTL/device run uses the full
         * [BenchmarkCorpusSeeder.DEFAULT_CORPUS_SIZE]. Defaults to the DoD floor of 10,000.
         */
        const val EXTRA_CORPUS_SIZE = "corpus_size"

        /**
         * Actively decode one seeded thumbnail and log the result, so the CI logcat carries a
         * grep-able proof that the corpus exercises REAL decode (not the old id-miss placeholder),
         * independent of the frame-timing metric. Line: `JGALLERY_BENCH_DECODE …`.
         */
        private fun logDecodeSelfCheck(context: android.content.Context, items: List<MediaItem>) {
            val sample = items.firstOrNull() ?: run {
                Log.w(BenchmarkCorpusSeeder.TAG, "JGALLERY_BENCH_DECODE decodeOk=false reason=empty_corpus")
                return
            }
            val ok = runCatching {
                context.contentResolver.openInputStream(BenchmarkCorpusSeeder.contentUriFor(sample)).use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    val dims = "${bmp?.width}x${bmp?.height}"
                    bmp?.recycle()
                    dims
                }
            }.getOrElse { "decode_error:${it.message}" }
            Log.i(
                BenchmarkCorpusSeeder.TAG,
                "JGALLERY_BENCH_DECODE decodeOk=${!ok.startsWith("decode_error")} " +
                    "seededCount=${items.size} firstId=${sample.id.value} " +
                    "declaredDims=${sample.width}x${sample.height} decodedDims=$ok mime=${sample.mimeType}",
            )
        }
    }
}
