package com.appblish.jgallery.benchmark

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import com.appblish.jgallery.feature.photos.PhotosScreen
import com.appblish.jgallery.feature.photos.PhotosUiState
import com.appblish.jgallery.feature.photos.WindowedPhotosTimeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
 *
 * ## Real-pipeline mode (APP-699)
 * The default [BenchmarkGrid] path hand-builds ONE timeline and feeds the stateless `PhotosScreen` —
 * it measures the list/layout + decode hot path in isolation but BYPASSES the production data path
 * (Room → `CachedMediaIndexRepository` → `PhotosViewModel` → `buildPhotosTimeline`), which APP-697
 * named the #1 scroll-perf suspect. `--ez bench_real_pipeline true` selects [RealPipelineGrid], which
 * seeds the corpus into MediaStore and then renders the REAL, Hilt-injected `PhotosScreen()` so peak
 * memory / query cost / frame timing reflect the whole production pipeline, not a shortcut. The
 * activity is [AndroidEntryPoint] purely so that real path resolves its `hiltViewModel()`; this
 * annotation lives in the benchmark source set only and never reaches a shipped APK.
 */
@AndroidEntryPoint
@OptIn(ExperimentalComposeUiApi::class) // testTagsAsResourceId — the supported UiAutomator seam
class PhotosBenchmarkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val corpusSize = intent.getIntExtra(EXTRA_CORPUS_SIZE, BenchmarkCorpusSeeder.DEFAULT_CORPUS_SIZE)
        val optedIn = intent.getBooleanExtra(EXTRA_OPT_IN, false)
        val cleanupMode = intent.getBooleanExtra(EXTRA_CLEANUP, false)
        val realPipeline = intent.getBooleanExtra(EXTRA_REAL_PIPELINE, false)
        val appContext = applicationContext

        // Install the counting image loader BEFORE setContent (before the grid's first image request)
        // so it wins the lazy SingletonImageLoader resolution. Real-pipeline mode only — the
        // layout-only lane keeps the untouched production loader.
        if (realPipeline && optedIn && !cleanupMode) {
            BenchDecodeCounters.install(appContext)
        }

        setContent {
            JGalleryTheme {
                when {
                    // (1) One-shot cleanup path (`--ez bench_cleanup true`): the macrobenchmark's
                    // post-run teardown and the operator's "purge the leaked assets" button both land
                    // here. Deletes every synthetic asset, then shows a UiAutomator-visible done tag.
                    cleanupMode -> CleanupScreen(appContext)

                    // (2a) Real-pipeline opt-in (`--ez bench_real_pipeline true --ez bench_opt_in
                    // true`): seed the corpus then render the REAL Hilt `PhotosScreen`, exercising the
                    // Room → repository → ViewModel → timeline path (APP-699).
                    realPipeline && optedIn -> RealPipelineGrid(appContext, corpusSize)

                    // (2b) Explicit opt-in (`--ez bench_opt_in true`, passed only by the macrobenchmark
                    // or a deliberate adb launch): the layout-only seed-and-scroll benchmark surface.
                    optedIn -> BenchmarkGrid(appContext, corpusSize)

                    // (3) Casual tap-launch / no opt-in: NEVER seed (that is what polluted JD's real
                    // library, APP-458). Show a guard screen that explains how to run it and offers a
                    // one-tap cleanup for any assets already leaked onto this device.
                    else -> GuardScreen(appContext)
                }
            }
        }
    }

    /**
     * Real-pipeline benchmark surface (APP-699). Seeds the corpus into MediaStore (idempotent,
     * off-main), then — once seeding is done so the production sync enumerates a fully-populated
     * library — renders the REAL Hilt-injected `PhotosScreen()`. That drives the whole production
     * pipeline: the repository's background sync upserts the corpus into Room, Room's
     * `SELECT * FROM media` flow feeds `CachedMediaIndexRepository.applyQuery` (filter + sort), and
     * `PhotosViewModel` re-derives `buildPhotosTimeline` (a second sort + the per-cell arrays) before
     * Compose ever sees a frame. `photos_grid` appears only once that path yields Content, so the
     * macrobenchmark's grid-ready wait doubles as a "Room populated to N items" barrier.
     *
     * A light background loop logs the decode / cache-hit counters (`JGALLERY_BENCH_DECODE_COUNTS`)
     * every [COUNTER_LOG_INTERVAL_MS] so the logcat timeline shows decodes plateau while cache hits
     * climb on a re-scroll — the proof of zero re-decode.
     */
    @Composable
    private fun RealPipelineGrid(appContext: Context, corpusSize: Int) {
        // Seed first (await) so the first production sync sees the whole corpus — avoids a race where
        // an empty first sync yields the Empty state before the inserts' change signal re-syncs.
        val seeded by produceState(initialValue = false, corpusSize) {
            withContext(Dispatchers.IO) {
                BenchmarkCorpusSeeder.seed(appContext, corpusSize, optedIn = true)
            }
            value = true
        }

        if (seeded) {
            // Periodic counter readout for the logcat proof (APP-699 decodeCount / cacheHit).
            LaunchedEffect(Unit) {
                while (isActive) {
                    delay(COUNTER_LOG_INTERVAL_MS)
                    android.util.Log.i(
                        BenchDecodeCounters.TAG,
                        "JGALLERY_BENCH_DECODE_COUNTS ${BenchDecodeCounters.snapshot()}",
                    )
                }
            }
            // The default overload injects the REAL PhotosViewModel via hiltViewModel(); every
            // callback stays at its no-op default (we only scroll). testTagsAsResourceId exposes
            // `photos_grid` to UiAutomator.
            PhotosScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true },
            )
        } else {
            // Pre-seed placeholder — deliberately NOT `photos_grid`, so the macrobenchmark keeps
            // waiting through the seed + first sync rather than measuring an empty screen.
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

    /** The real benchmark surface: seed-once (opted-in) then render the 10k+ tile grid. */
    @Composable
    private fun BenchmarkGrid(appContext: Context, corpusSize: Int) {
        var columns by remember { mutableStateOf(ColumnCount.DEFAULT) }
        // Await the process-wide, seed-once timeline. First launch seeds; recreations resolve
        // instantly from the cached Deferred.
        val timeline by produceState<WindowedPhotosTimeline?>(initialValue = null, corpusSize) {
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
            // Before the one-time corpus seed completes, show a tagged placeholder — deliberately NOT
            // `photos_grid`, so the macrobenchmark keeps waiting through the seed rather than
            // measuring an empty screen.
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

    /**
     * Delete every synthetic bench asset, then surface a UiAutomator-visible `bench_cleanup_done` tag
     * so [PhotosScrollBenchmark]'s teardown can confirm the purge finished. Runs the delete off the
     * main thread.
     */
    @Composable
    private fun CleanupScreen(appContext: Context) {
        val deleted by produceState<Int?>(initialValue = null) {
            value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val removed = BenchmarkCorpusSeeder.cleanup(appContext)
                // APP-699 real-pipeline mode also POPULATES the on-disk Room index cache (the
                // production sync upserts the seeded corpus). That DB is a rebuildable cache, not a
                // source of truth, so dropping it here leaves no bench rows behind for a later normal
                // launch. Named by the string literal on purpose — MediaIndexDatabase.NAME is internal
                // to :core:index and unreachable from the benchmark source set. Harmless (returns
                // false) when the layout-only lane never touched Room.
                val droppedIndex = appContext.deleteDatabase(MEDIA_INDEX_DB_NAME)
                android.util.Log.i(
                    BenchmarkCorpusSeeder.TAG,
                    "JGALLERY_BENCH_CLEANUP_INDEX droppedIndexCache=$droppedIndex",
                )
                removed
            }
        }
        val done = deleted != null
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    testTagsAsResourceId = true
                    testTag = if (done) "bench_cleanup_done" else "bench_cleanup_running"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "Removed $deleted benchmark asset(s)." else "Removing benchmark assets…",
            )
        }
    }

    /**
     * Shown on a plain tap-launch (no opt-in). Never seeds. Explains how to run the benchmark
     * deliberately and offers a one-tap purge of any assets already leaked onto this device.
     */
    @Composable
    private fun GuardScreen(appContext: Context) {
        var cleaning by remember { mutableStateOf(false) }
        if (cleaning) {
            CleanupScreen(appContext)
            return
        }
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .semantics {
                        testTagsAsResourceId = true
                        testTag = "bench_guard"
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text("JGallery benchmark fixture")
                Text(
                    "This screen will NOT seed synthetic photos on a plain launch — seeding pollutes " +
                        "your real photo library. Run it deliberately via the macrobenchmark, or with " +
                        "adb: am start -n $packageName/$BENCH_ACTIVITY --ez $EXTRA_OPT_IN true",
                )
                Text(
                    "Remove any leaked benchmark assets from this device",
                    modifier = Modifier
                        .clickable { cleaning = true }
                        .semantics { testTag = "bench_cleanup_action" },
                )
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
         * Hard opt-in gate (APP-458): only when this boolean extra is `true` does the activity seed.
         * The macrobenchmark passes it; a casual tap-launch does not, so the fixture can never pollute
         * a real library by accident.
         */
        const val EXTRA_OPT_IN = "bench_opt_in"

        /**
         * When `true` the activity deletes every synthetic bench asset and exits — the fixture
         * teardown and the one-shot "purge leaked assets" path (APP-458).
         */
        const val EXTRA_CLEANUP = "bench_cleanup"

        /**
         * When `true` (with [EXTRA_OPT_IN]) the activity renders the REAL Hilt `PhotosScreen` so the
         * scroll drives the production Room → repository → ViewModel → timeline path (APP-699), rather
         * than the layout-only hand-built timeline. Defaults to `false` (the layout-only lane).
         */
        const val EXTRA_REAL_PIPELINE = "bench_real_pipeline"

        const val BENCH_ACTIVITY = "com.appblish.jgallery.benchmark.PhotosBenchmarkActivity"

        /**
         * On-disk name of the index Room cache (mirror of the internal `MediaIndexDatabase.NAME`,
         * which is not visible from this source set). Only referenced by the self-cleaning teardown to
         * drop the rebuildable cache the real-pipeline sync populated.
         */
        const val MEDIA_INDEX_DB_NAME = "media-index.db"

        /** Cadence of the decode/cache-hit counter readout in real-pipeline mode. */
        const val COUNTER_LOG_INTERVAL_MS = 750L
    }
}

/**
 * Process-wide, seed-once cache of the benchmark corpus timeline. The first caller kicks off the
 * (idempotent) seed + query on [Dispatchers.IO]; every later caller awaits the same [Deferred], so
 * the seed never restarts even when the activity is recreated between WARM benchmark iterations.
 */
private object BenchmarkCorpusHolder {

    @Volatile
    private var deferred: Deferred<WindowedPhotosTimeline?>? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun timelineAsync(context: Context, corpusSize: Int): Deferred<WindowedPhotosTimeline?> {
        deferred?.let { return it }
        return synchronized(this) {
            deferred ?: scope.async {
                // Only ever reached from the opted-in benchmark path (PhotosBenchmarkActivity gates on
                // EXTRA_OPT_IN before calling this), so seeding here is the deliberate, guarded case.
                val items = BenchmarkCorpusSeeder.seed(
                    context.applicationContext,
                    corpusSize,
                    optedIn = true,
                )
                logDecodeSelfCheck(context, items)
                if (items.isEmpty()) {
                    null
                } else {
                    val zone = ZoneId.systemDefault()
                    // APP-700: PhotosUiState.Content now requires a WindowedPhotosTimeline. The
                    // layout-only lane has no :core:index skeleton, so build one from the in-memory
                    // corpus via the fromItems factory (all items land in window page 0).
                    WindowedPhotosTimeline.fromItems(
                        items = items,
                        zone = zone,
                        today = LocalDate.now(zone),
                    )
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
