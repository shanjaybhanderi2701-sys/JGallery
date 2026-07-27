package com.appblish.jgallery.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The headline scroll-jank gate (spec §11 DoD; measurement fix APP-390; real-pipeline + scale +
 * peak-memory extension APP-699, parent APP-696).
 *
 * ## What this measures now (APP-699)
 * Two lanes over the same seeded real-photo corpus, selectable per test:
 *  - **layout-only** ([Mode.LAYOUT_ONLY]) — `PhotosBenchmarkActivity` hand-builds one timeline and
 *    renders the stateless `PhotosScreen`. Isolates the list/layout + decode hot path (the historical
 *    APP-390 lane), so a regression there is not masked by data-path cost.
 *  - **real-pipeline** ([Mode.REAL_PIPELINE]) — the activity renders the REAL Hilt `PhotosScreen`, so
 *    the scroll drives the production Room → `CachedMediaIndexRepository` → `PhotosViewModel` →
 *    `buildPhotosTimeline` path that APP-697 named the #1 scroll-perf suspect. This is the lane whose
 *    peak-memory / frame numbers reflect production.
 *
 * Every lane runs at a parameterised [Scale] (5k / 15k / 50k), captures **peak memory** per iteration
 * from `dumpsys meminfo` (`JGALLERY_BENCH_MEM …`), and does a scroll-down THEN scroll-up pass so the
 * decode / cache-hit counters (`JGALLERY_BENCH_DECODE_COUNTS …`, real-pipeline only) prove zero
 * re-decode on the return fling. A WARM and a COLD startup variant are provided.
 *
 * ## Metrics
 * [FrameTimingMetric] reports frameDurationCpuMs P50/P90/P99. A jank PERCENT — frames over the
 * ~16.67ms budget — is captured separately from `dumpsys gfxinfo` per iteration and logged as
 * `JGALLERY_BENCH_JANK …`, because FrameTimingMetric does not emit a jank ratio directly. Peak PSS /
 * Java-heap / native-heap come from `dumpsys meminfo` (`JGALLERY_BENCH_MEM …`).
 *
 * ## Scale & infra (APP-699 infra dependency)
 * 5k / 15k run on the local emulator now. The authoritative **50k** run needs a large / repartitioned
 * AVD or a physical device (~8–12 GB; the shared AVD has ~3.8 GB free) plus a low-end tier — the
 * harness CODE is not blocked by that, only the 50k RUN is. Physical-device execution stays the
 * authoritative source for headline numbers (host-GPU stalls make emulator numbers directional).
 */
@RunWith(AndroidJUnit4::class)
class PhotosScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Fixture teardown (APP-458 + APP-699): delete every synthetic file the seed wrote AND drop the
     * rebuildable Room index cache the real-pipeline sync populated, so the benchmark leaves the
     * device exactly as it found it. As an `@After`, JUnit runs this after the test whether it PASSED
     * or FAILED/aborted, so a crashed run cleans up too. It drives the target app's cleanup mode
     * out-of-process (the seeder lives in the app's benchmark variant, unreachable from this test
     * process) and waits for the `bench_cleanup_done` tag as proof the purge finished. If the process
     * is hard-killed before this runs, the same `--ez bench_cleanup true` launch (or the guard
     * screen's cleanup button) is the manual one-shot recovery path.
     */
    @After
    fun tearDownCorpus() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand(
            "am start-activity -W -n $TARGET_PACKAGE/$TARGET_ACTIVITY " +
                "--ez bench_cleanup true --display 0 --activity-clear-task",
        )
        val cleaned = device.wait(Until.hasObject(By.res("bench_cleanup_done")), CLEANUP_WAIT_MS)
        android.util.Log.i(
            "JGalleryBench",
            "JGALLERY_BENCH_TEARDOWN cleanupConfirmed=${cleaned == true}",
        )
    }

    // ---- Real-pipeline lane (APP-699 headline) -------------------------------------------------

    /** 5k real-pipeline WARM — runs on the local emulator now. */
    @Test
    fun realPipeline_5k_warm() = runScroll(Scale.SMALL_5K, Mode.REAL_PIPELINE, StartupMode.WARM)

    /** 15k real-pipeline WARM — runs on the local emulator now (the DoD "runs locally now" gate). */
    @Test
    fun realPipeline_15k_warm() = runScroll(Scale.MEDIUM_15K, Mode.REAL_PIPELINE, StartupMode.WARM)

    /** 50k real-pipeline WARM — the authoritative scale; gated on the large-AVD / device infra. */
    @Test
    fun realPipeline_50k_warm() = runScroll(Scale.LARGE_50K, Mode.REAL_PIPELINE, StartupMode.WARM)

    /**
     * 15k real-pipeline COLD — the cold-start pass APP-699 adds. COLD kills the process between
     * iterations; on the CI emulator the relaunch can land on a display UiAutomation can't see (the
     * APP-382 finding), so treat emulator COLD numbers as directional and rely on the physical-device
     * COLD run for the headline.
     */
    @Test
    fun realPipeline_15k_cold() = runScroll(Scale.MEDIUM_15K, Mode.REAL_PIPELINE, StartupMode.COLD)

    // ---- Layout-only lane (APP-390 baseline; isolates list/layout + decode) --------------------

    /** 15k layout-only WARM — the historical isolation lane, kept for regression triangulation. */
    @Test
    fun layoutOnly_15k_warm() = runScroll(Scale.MEDIUM_15K, Mode.LAYOUT_ONLY, StartupMode.WARM)

    /**
     * The single measured core, parameterised by [scale], [mode] and [startup]. Seeds a real-photo
     * corpus of [Scale.corpus] files, flings the `photos_grid` down then back up, and records
     * FrameTimingMetric plus per-iteration jank%, peak memory, and (real-pipeline) decode/cache-hit
     * counts.
     */
    private fun runScroll(scale: Scale, mode: Mode, startup: StartupMode) {
        val corpus = corpusSizeOverride ?: scale.corpus
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = if (startup == StartupMode.COLD) COLD_ITERATIONS else ITERATIONS,
            startupMode = startup,
            setupBlock = {
                // The real-pipeline sync enumerates MediaStore via :core:storage, so grant the media
                // read permissions the production onboarding would have (best-effort; harmless if the
                // build's targetSdk maps them differently). Own-app rows are readable regardless, but
                // this keeps the enumeration faithful to production on a shared device.
                for (perm in MEDIA_READ_PERMISSIONS) {
                    runCatching { device.executeShellCommand("pm grant $TARGET_PACKAGE $perm") }
                }
                val realFlag = if (mode == Mode.REAL_PIPELINE) "--ez bench_real_pipeline true " else ""
                for (attempt in 0 until LAUNCH_ATTEMPTS) {
                    val status = device.executeShellCommand(
                        "am start-activity -W -n $TARGET_PACKAGE/$TARGET_ACTIVITY " +
                            "--ei corpus_size $corpus --ez bench_opt_in true " +
                            realFlag +
                            "--display 0 --activity-clear-task",
                    )
                    check(!status.contains("Error:", ignoreCase = true)) {
                        "am start-activity of PhotosBenchmarkActivity on display 0 failed " +
                            "(attempt $attempt):\n$status"
                    }
                    // Grid queryable on display 0 → seed + first sync finished and the real-photo grid
                    // is up. The wait is long because the FIRST iteration seeds thousands of files (and
                    // in real-pipeline mode also waits for Room to be populated) before first render.
                    if (device.wait(Until.hasObject(By.res("photos_grid")), SEED_AWARE_PROBE_MS) == true ||
                        device.wait(Until.hasObject(By.scrollable(true)), LAUNCH_PROBE_MS) == true
                    ) {
                        break
                    }
                }
            },
        ) {
            // Reset frame stats so the gfxinfo jank% below covers only THIS iteration's flings.
            device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE reset")

            // Grid-ready gate inside the measured block (already covered by setupBlock; last-resort).
            device.wait(Until.findObject(By.res("photos_grid")), GRID_WAIT_MS)
                ?: device.wait(Until.findObject(By.scrollable(true)), SCROLLABLE_FALLBACK_MS)
                ?: run {
                    val dump = java.io.ByteArrayOutputStream().use { os ->
                        runCatching { device.dumpWindowHierarchy(os) }
                        os.toString()
                    }
                    error(
                        "Neither photos_grid (${GRID_WAIT_MS}ms) nor any scrollable node " +
                            "(${SCROLLABLE_FALLBACK_MS}ms) found after $LAUNCH_ATTEMPTS launch " +
                            "attempts — mode=$mode corpus=$corpus. PhotosBenchmarkActivity is not " +
                            "showing the real-photo grid (seed/sync may have failed). Window " +
                            "hierarchy follows:\n" + dump.take(12000),
                    )
                }

            var peakPssKb = 0L
            // Scroll DOWN then back UP: the return pass re-visits tiles already decoded on the way
            // down, so the real-pipeline decode/cache-hit counters must show cache hits (not new
            // decodes) — the zero-re-decode proof. Re-acquire a FRESH UiObject2 before EVERY fling —
            // holding one handle across flings throws StaleObjectException once the grid recycles nodes.
            for (direction in listOf(Direction.DOWN, Direction.UP)) {
                repeat(SCROLLS_PER_ITERATION) {
                    val grid = device.findObject(By.res("photos_grid"))
                        ?: device.findObject(By.scrollable(true))
                        ?: return@repeat
                    grid.setGestureMargin(device.displayWidth / 5)
                    grid.fling(direction)
                    device.waitForIdle()
                }
                // Sample memory at the end of each pass; peak is the max across the whole iteration.
                peakPssKb = maxOf(peakPssKb, readTotalPssKb(device))
            }

            // Peak memory for this iteration (APP-699). Full dumpsys line logged for the Java/native
            // breakdown; the compact grep line carries the headline PSS.
            val meminfo = device.executeShellCommand("dumpsys meminfo $TARGET_PACKAGE")
            android.util.Log.i("JGalleryBench", "JGALLERY_BENCH_MEM ${parseMem(meminfo, peakPssKb)}")

            // Capture jank% for this iteration from gfxinfo and log it grep-ably. FrameTimingMetric owns
            // the frameDurationCpuMs percentiles; this adds the over-budget frame ratio the DoD asks for.
            val gfxinfo = device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE")
            android.util.Log.i("JGalleryBench", "JGALLERY_BENCH_JANK ${parseJank(gfxinfo)}")

            if (mode == Mode.REAL_PIPELINE) {
                // The app logs JGALLERY_BENCH_DECODE_COUNTS itself on a timer; this final marker pins
                // the end-of-iteration snapshot next to the frame/mem numbers for the same iteration.
                android.util.Log.i(
                    "JgalleryBench",
                    "JGALLERY_BENCH_DECODE_COUNTS_MARK iterationEnd corpus=$corpus",
                )
            }
        }
    }

    /** The benchmark lane: real production data path vs. isolated list/layout. */
    private enum class Mode { REAL_PIPELINE, LAYOUT_ONLY }

    /** Corpus scales (APP-699). 5k/15k run locally now; 50k is the infra-gated authoritative scale. */
    private enum class Scale(val corpus: Int) {
        SMALL_5K(5_000),
        MEDIUM_15K(15_000),
        LARGE_50K(50_000),
    }

    private companion object {
        const val TARGET_PACKAGE = "com.appblish.jgallery"
        const val TARGET_ACTIVITY = "com.appblish.jgallery.benchmark.PhotosBenchmarkActivity"
        const val ITERATIONS = 5
        const val COLD_ITERATIONS = 3
        const val SCROLLS_PER_ITERATION = 10

        /** Media reads the production sync would hold; granted best-effort in setup (API-version-tolerant). */
        val MEDIA_READ_PERMISSIONS = listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE",
        )

        /**
         * Corpus-size override for an ad-hoc run (`-P…androidx…jgallery.bench.corpusSize=N`); when
         * absent each test uses its [Scale.corpus]. Lets a smoke run shrink the corpus without editing
         * the scale enum.
         */
        val corpusSizeOverride: Int?
            get() = InstrumentationRegistry.getArguments()
                .getString("jgallery.bench.corpusSize")
                ?.toIntOrNull()

        const val LAUNCH_ATTEMPTS = 4

        /** Per-attempt grid-ready probe sized for the one-time first-iteration seed + first sync. */
        const val SEED_AWARE_PROBE_MS = 180_000L

        /** Warm-iteration probe once the corpus + Room cache already exist (grid appears near-instantly). */
        const val LAUNCH_PROBE_MS = 12_000L

        /** Grid-ready wait inside the measured block (already covered by setupBlock; last-resort gate). */
        const val GRID_WAIT_MS = 180_000L

        /** Extra wait for any scrollable node once the tagged lookup misses. */
        const val SCROLLABLE_FALLBACK_MS = 10_000L

        /** Teardown wait: deleting up to 50k rows from MediaStore + dropping the Room cache takes a moment. */
        const val CLEANUP_WAIT_MS = 180_000L

        /**
         * Parse `dumpsys gfxinfo` into a compact `jankyFramePercent=… totalFrames=…` string. The
         * relevant lines look like `Total frames rendered: 812` and `Janky frames: 96 (11.82%)`.
         */
        fun parseJank(gfxinfo: String): String {
            val total = Regex("Total frames rendered: (\\d+)").find(gfxinfo)?.groupValues?.get(1) ?: "?"
            val janky = Regex("Janky frames: (\\d+) \\(([\\d.]+)%\\)").find(gfxinfo)
            val jankPct = janky?.groupValues?.get(2) ?: "?"
            val jankCount = janky?.groupValues?.get(1) ?: "?"
            return "jankyFramePercent=$jankPct jankyFrames=$jankCount totalFrames=$total"
        }

        /** `TOTAL PSS` (App Summary) for the target, in KB; 0 if the line is absent. */
        fun readTotalPssKb(device: UiDevice): Long {
            val meminfo = device.executeShellCommand("dumpsys meminfo $TARGET_PACKAGE")
            return Regex("TOTAL(?: PSS)?:?\\s+(\\d+)").find(meminfo)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        }

        /**
         * Compact peak-memory line: the sampled peak PSS across the iteration's scroll passes, plus
         * the Java-heap / native-heap breakdown from the final dump (KB). `dumpsys meminfo` App Summary
         * carries `Java Heap:`, `Native Heap:` and a `TOTAL PSS:` line.
         */
        fun parseMem(meminfo: String, peakPssKb: Long): String {
            fun kb(label: String) =
                Regex("$label:?\\s+(\\d+)").find(meminfo)?.groupValues?.get(1) ?: "?"
            return "peakPssKb=$peakPssKb javaHeapKb=${kb("Java Heap")} " +
                "nativeHeapKb=${kb("Native Heap")} totalPssKb=${kb("TOTAL(?: PSS)?")}"
        }
    }
}
