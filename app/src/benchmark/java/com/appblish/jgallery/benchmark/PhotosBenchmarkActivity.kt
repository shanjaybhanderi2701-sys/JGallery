package com.appblish.jgallery.benchmark

import android.content.Context
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
import com.appblish.jgallery.feature.photos.PhotosTimeline
import com.appblish.jgallery.feature.photos.PhotosUiState
import com.appblish.jgallery.feature.photos.buildPhotosTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
 * false ~1.9% jank pass. It now renders tiles carrying REAL MediaStore row-ids (seeded by
 * [BenchmarkCorpusSeeder]) so a fling drives the true `loadThumbnail` → decode → Coil path.
 *
 * ## Seed ONCE, render instantly (CI reliability)
 * The corpus is seeded + queried exactly once per process into [BenchmarkCorpusHolder]; every later
 * activity recreation (the WARM macrobenchmark relaunches each iteration, see APP-382) awaits the
 * SAME cached result and renders the grid immediately. That keeps WARM relaunches as fast as the old
 * synthetic fixture — critical on the CI emulator, whose display/focus race trips when a relaunch is
 * slow — while now measuring real decode. The grid (`photos_grid` tag) only appears once the corpus
 * is ready, so the macrobenchmark's grid-ready wait doubles as a one-time seed barrier.
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
                // Await the process-wide, seed-once timeline. First launch seeds; recreations resolve
                // instantly from the cached Deferred.
                val timeline by produceState<PhotosTimeline?>(initialValue = null, corpusSize) {
                    value = BenchmarkCorpusHolder.timelineAsync(appContext, corpusSize).await()
                }

                val t = timeline
                if (t != null) {
                    PhotosScreen(
                        state = PhotosUiState.Content(t),
                        columns = columns,
                        onColumnsChange = { columns = it },
                        modifier = Modifier
                            .fillMaxSize()
                            // Expose Compose testTags as View resource-ids for UiAutomator.
                            .semantics { testTagsAsResourceId = true },
                    )
                } else {
                    // Before the one-time corpus seed completes, show a tagged placeholder —
                    // deliberately NOT `photos_grid`, so the macrobenchmark keeps waiting through the
                    // seed rather than measuring an empty screen.
                    Box(
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
    }
}

/**
 * Process-wide, seed-once cache of the benchmark corpus timeline. The first caller kicks off the
 * (idempotent) seed + query on [Dispatchers.IO]; every later caller awaits the same [Deferred], so
 * the seed never restarts even when the activity is recreated between WARM benchmark iterations.
 */
private object BenchmarkCorpusHolder {

    @Volatile
    private var deferred: Deferred<PhotosTimeline?>? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun timelineAsync(context: Context, corpusSize: Int): Deferred<PhotosTimeline?> {
        deferred?.let { return it }
        return synchronized(this) {
            deferred ?: scope.async {
                val items = BenchmarkCorpusSeeder.seed(context.applicationContext, corpusSize)
                logDecodeSelfCheck(context, items)
                if (items.isEmpty()) {
                    null
                } else {
                    val zone = ZoneId.systemDefault()
                    buildPhotosTimeline(items = items, zone = zone, today = LocalDate.now(zone))
                }
            }.also { deferred = it }
        }
    }

    /**
     * Actively decode one seeded thumbnail and log the result, so the CI logcat carries a grep-able
     * proof that the corpus exercises REAL decode (not the old id-miss placeholder), independent of
     * the frame-timing metric. Line: `JGALLERY_BENCH_DECODE …`.
     */
    private fun logDecodeSelfCheck(context: Context, items: List<MediaItem>) {
        val sample = items.firstOrNull() ?: run {
            Log.w(BenchmarkCorpusSeeder.TAG, "JGALLERY_BENCH_DECODE decodeOk=false reason=empty_corpus")
            return
        }
        val decoded = runCatching {
            context.contentResolver.openInputStream(BenchmarkCorpusSeeder.contentUriFor(sample)).use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                val dims = if (bmp != null) "${bmp.width}x${bmp.height}" else "null"
                bmp?.recycle()
                dims
            }
        }.getOrElse { "decode_error:${it.message}" }
        Log.i(
            BenchmarkCorpusSeeder.TAG,
            "JGALLERY_BENCH_DECODE decodeOk=${decoded[0].isDigit()} seededCount=${items.size} " +
                "firstId=${sample.id.value} declaredDims=${sample.width}x${sample.height} " +
                "decodedDims=$decoded mime=${sample.mimeType}",
        )
    }
}
